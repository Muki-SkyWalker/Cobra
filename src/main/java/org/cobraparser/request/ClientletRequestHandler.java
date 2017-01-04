/*
    GNU GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public
    License as published by the Free Software Foundation; either
    verion 2 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
 */
/*
 * Created on Mar 5, 2005
 */
package org.cobraparser.request;

import java.io.IOException;
import java.net.URL;

import org.eclipse.jdt.annotation.NonNull;
import org.cobraparser.clientlet.Clientlet;
import org.cobraparser.clientlet.ClientletAccess;
import org.cobraparser.clientlet.ClientletContext;
import org.cobraparser.clientlet.ClientletException;
import org.cobraparser.clientlet.ClientletRequest;
import org.cobraparser.clientlet.ClientletResponse;
import org.cobraparser.clientlet.ComponentContent;
import org.cobraparser.context.ClientletContextImpl;
import org.cobraparser.context.ClientletFactory;
import org.cobraparser.gui.FramePanel;
import org.cobraparser.gui.WindowCallback;
import org.cobraparser.security.LocalSecurityManager;
import org.cobraparser.ua.NavigatorProgressEvent;
import org.cobraparser.ua.ProgressType;
import org.cobraparser.ua.RequestType;
import org.cobraparser.ua.UserAgentContext;
import org.cobraparser.util.EventDispatch;
import org.cobraparser.util.Urls;

/**
 * @author J. H. S.
 */
public class ClientletRequestHandler extends AbstractRequestHandler {
  private final WindowCallback windowCallback;
  private final FramePanel frame;

  /**
   * For progress events, but a null event is also fired when the content is
   * set.
   */
  public final EventDispatch evtProgress = new EventDispatch();

  public ClientletRequestHandler(final ClientletRequest request, final WindowCallback clientletUI, final FramePanel frame,
      final UserAgentContext uaContext) {
    super(request, frame.getComponent(), uaContext);
    this.windowCallback = clientletUI;
    this.frame = frame;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * net.sourceforge.xamj.http.RequestHandler#handleException(java.lang.Exception)
   */
  @Override
  public boolean handleException(final ClientletResponse response, final Throwable exception, final RequestType requestType)
      throws ClientletException {
    if (this.windowCallback != null) {
      this.windowCallback.handleError(this.frame, response, exception, requestType);
      return true;
    } else {
      return false;
    }
  }

  private volatile java.util.Properties windowProperties = null;

  public java.util.Properties getContextWindowProperties() {
    return this.windowProperties;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * net.sourceforge.xamj.http.RequestHandler#processResponse(org.xamjwg.dom
   * .ClientletResponse)
   */
  @Override
  public void processResponse(final ClientletResponse response) throws ClientletException, IOException {
    if (this.windowCallback != null) {
      this.windowCallback.handleDocumentAccess(this.frame, response);
    }
    final Clientlet clientlet = ClientletFactory.getInstance().getClientlet(this.getRequest(), response);
    if (clientlet == null) {
      throw new ClientletException("Unable to find clientlet for response: " + response + ".");
    }
    this.frame.setProgressEvent(null);
    final ClientletContext ctx = new ClientletContextImpl(this.frame, this.request, response) {
      @Override
      public void setResultingContent(final ComponentContent content) {
        // Frame content should be replaced as
        // soon as this method is called to allow
        // for incremental rendering.
        super.setResultingContent(content);
        windowProperties = this.getOverriddingWindowProperties();
        // Replace content before firing progress
        // event to avoid window flickering.

        frame.replaceContent(response, content);
        evtProgress.fireEvent(null);
      }
    };
    final ClientletContext prevCtx = ClientletAccess.getCurrentClientletContext();
    ClientletAccess.setCurrentClientletContext(ctx);
    final ThreadGroup prevThreadGroup = LocalSecurityManager.getCurrentThreadGroup();
    // TODO: Thread group needs to be thought through. It's retained in
    // memory, and we need to return the right one in the GUI thread as well.
    final ThreadGroup newThreadGroup = null; // new org.cobraparser.context.ClientletThreadGroupImpl("CTG-" + ctx.getResponse().getResponseURL().getHost(), ctx);
    LocalSecurityManager.setCurrentThreadGroup(newThreadGroup);
    // Set context class loader because the extension was likely
    // compiled to require extension libraries.
    final Thread currentThread = Thread.currentThread();
    final ClassLoader prevClassLoader = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(clientlet.getClass().getClassLoader());
    try {
      clientlet.process(ctx);
    } finally {
      currentThread.setContextClassLoader(prevClassLoader);
      LocalSecurityManager.setCurrentThreadGroup(prevThreadGroup);
      ClientletAccess.setCurrentClientletContext(prevCtx);
    }
    this.frame.informResponseProcessed(response);
  }

  @Override
  public void handleProgress(final ProgressType progressType, final @NonNull URL url, final String method, final int value, final int max) {
    final NavigatorProgressEvent event = new NavigatorProgressEvent(this, this.frame, progressType, url, method, value, max);
    this.evtProgress.fireEvent(event);
    this.frame.setProgressEvent(event);
  }

  public static String getProgressMessage(final ProgressType progressType, final @NonNull URL url) {
    final String urlText = Urls.getNoRefForm(url);
    switch (progressType) {
    case CONNECTING:
      final String host = url.getHost();
      if ((host == null) || "".equals(host)) {
        return "Opening " + urlText;
      } else {
        return "Contacting " + host;
      }
    case SENDING:
      return "Sending data to " + urlText;
    case WAITING_FOR_RESPONSE:
      return "Waiting on " + urlText;
    case CONTENT_LOADING:
      return "Loading " + urlText;
    case BUILDING:
      return "Building " + urlText;
    case DONE:
      return "Processed " + urlText;
    default:
      return "[?]" + urlText;
    }
  }
}
