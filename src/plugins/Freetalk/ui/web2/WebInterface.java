/*
 * Freetalk - WebInterface.java - Copyright © 2010 David Roden
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugins.Freetalk.ui.web2;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import net.pterodactylus.util.template.Template;
import net.pterodactylus.util.template.TemplateFactory;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.exceptions.NoSuchIdentityException;
import plugins.Freetalk.ui.web2.page.CSSPage;
import plugins.Freetalk.ui.web2.page.Page;
import plugins.Freetalk.ui.web2.page.PageToadlet;
import plugins.Freetalk.ui.web2.page.PageToadletFactory;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.RedirectException;
import freenet.clients.http.SessionManager;
import freenet.clients.http.ToadletContainer;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.SessionManager.Session;
import freenet.l10n.BaseL10n;

/**
 * Main web interface class.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class WebInterface {

	/** The plugin. */
	private final Freetalk freetalkPlugin;

	/** The session manager. */
	private final SessionManager sessionManager;

	/** The registered toadlets. */
	private final List<PageToadlet> pageToadlets = new ArrayList<PageToadlet>();

	/**
	 * Creates a new web interface.
	 *
	 * @param freetalkPlugin
	 *            The plugin
	 */
	public WebInterface(Freetalk freetalkPlugin) {
		this.freetalkPlugin = freetalkPlugin;
		try {
			sessionManager = new SessionManager(new URI("/Freetalk"));
		} catch (URISyntaxException use1) {
			throw new RuntimeException("Could not create session manager.", use1);
		}
		registerToadlets();
	}

	/**
	 * Returns a reference to the {@link Freetalk} plugin itself.
	 *
	 * @return The Freetalk plugin
	 */
	public Freetalk getFreetalkPlugin() {
		return freetalkPlugin;
	}

	/**
	 * Returns the session manager.
	 *
	 * @return The session manager
	 */
	public SessionManager getSessionManager() {
		return sessionManager;
	}

	/**
	 * Returns the current session.
	 *
	 * @param request
	 *            The request to get the session for
	 * @return The session of the request, or {@code null} if there is no
	 *         session
	 */
	public Session getSession(Page.Request request) {
		try {
			return sessionManager.useSession(request.getToadletContext());
		} catch (RedirectException re1) {
			/* will not throw because we did not set a redirect URI. */
			return null;
		}
	}

	/**
	 * Returns the currently logged in {@link FTOwnIdentity own identity}.
	 *
	 * @param request
	 *            The request to get the logged in user for
	 * @return The logged in identity, or {@code null} if no identity is
	 *         currently logged in
	 */
	public FTOwnIdentity getOwnIdentity(Page.Request request) {
		Session session = getSession(request);
		if (session == null) {
			return null;
		}
		String userId = session.getUserID();
		try {
			FTOwnIdentity ownIdentity = freetalkPlugin.getIdentityManager().getOwnIdentity(userId);
			return ownIdentity;
		} catch (NoSuchIdentityException nsie1) {
			return null;
		}
	}

	//
	// ACTIONS
	//

	/**
	 * Terminates the plugin.
	 */
	public void terminate() {
		unregisterToadlets();
	}

	//
	// PRIVATE ACTIONS
	//

	/**
	 * Register all toadlets.
	 */
	private void registerToadlets() {
		BaseL10n l10n = freetalkPlugin.getBaseL10n();
		TemplateFactory templateFactory = new FreetalkTemplateFactory(l10n);

		Template welcomeTemplate = templateFactory.createTemplate(createReader("/plugins/Freetalk/ui/web/html/LogIn.html"));
		welcomeTemplate.set("formPassword", freetalkPlugin.getPluginRespirator().getToadletContainer().getFormPassword());

		Template indexTemplate = templateFactory.createTemplate(createReader("/plugins/Freetalk/ui/web/html/Index.html"));
		Template boardTemplate = templateFactory.createTemplate(createReader("/plugins/Freetalk/ui/web/html/Board.html"));

		Template boardsTemplate = templateFactory.createTemplate(createReader("/plugins/Freetalk/ui/web/html/Boards.html"));
		boardsTemplate.set("formPassword", freetalkPlugin.getPluginRespirator().getToadletContainer().getFormPassword());

		Template webOfTrustMissingTemplate = templateFactory.createTemplate(createReader("/plugins/Freetalk/ui/web/html/WebOfTrustMissing.html"));
		Template sessionExpiredTemplate = templateFactory.createTemplate(createReader("/plugins/Freetalk/ui/web/html/SessionExpired.html"));

		PageToadletFactory pageToadletFactory = new PageToadletFactory(freetalkPlugin.getPluginRespirator().getHLSimpleClient(), "/Freetalk/");
		pageToadlets.add(pageToadletFactory.createPageToadlet(new IndexPage(indexTemplate, l10n, this), "Index"));
		pageToadlets.add(pageToadletFactory.createPageToadlet(new LogInPage(welcomeTemplate, l10n, this), "LogIn"));
		pageToadlets.add(pageToadletFactory.createPageToadlet(new BoardsPage(boardsTemplate, l10n, this), "Boards"));
		pageToadlets.add(pageToadletFactory.createPageToadlet(new BoardPage(boardTemplate, l10n, this)));
		pageToadlets.add(pageToadletFactory.createPageToadlet(new WebOfTrustMissingPage(webOfTrustMissingTemplate, l10n, this)));
		pageToadlets.add(pageToadletFactory.createPageToadlet(new FreetalkTemplatePage("SessionExpired", sessionExpiredTemplate, l10n, "Page.SessionExpired.Title", this)));
		pageToadlets.add(pageToadletFactory.createPageToadlet(new CSSPage("css/", "/plugins/Freetalk/ui/web/css/")));

		ToadletContainer toadletContainer = freetalkPlugin.getPluginRespirator().getToadletContainer();
		toadletContainer.getPageMaker().addNavigationCategory("/Freetalk/", "Navigation.Menu.Name", "Navigation.Menu.Tooltip", freetalkPlugin);
		for (PageToadlet toadlet : pageToadlets) {
			String menuName = toadlet.getMenuName();
			if (menuName != null) {
				toadletContainer.register(toadlet, "Navigation.Menu.Name", toadlet.path(), true, "Navigation.Menu.Item." + menuName + ".Name", "Navigation.Menu.Item." + menuName + ".Tooltip", false, new AlwaysEnabledCallback());
			} else {
				toadletContainer.register(toadlet, null, toadlet.path(), true, false);
			}
		}
	}

	/**
	 * Unregisters all toadlets.
	 */
	private void unregisterToadlets() {
		ToadletContainer toadletContainer = freetalkPlugin.getPluginRespirator().getToadletContainer();
		for (PageToadlet pageToadlet : pageToadlets) {
			toadletContainer.unregister(pageToadlet);
		}
		toadletContainer.getPageMaker().removeNavigationCategory("Navigation.Menu.Name");
	}

	/**
	 * Creates a {@link Reader} from the {@link InputStream} for the resource
	 * with the given name.
	 *
	 * @param resourceName
	 *            The name of the resource
	 * @return A {@link Reader} for the resource
	 */
	private Reader createReader(String resourceName) {
		try {
			return new InputStreamReader(getClass().getResourceAsStream(resourceName), "UTF-8");
		} catch (UnsupportedEncodingException uee1) {
			return null;
		}
	}

	/**
	 * {@link LinkEnabledCallback} implementation that always returns {@code
	 * true} when {@link LinkEnabledCallback#isEnabled(ToadletContext)} is
	 * called.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public class AlwaysEnabledCallback implements LinkEnabledCallback {

		/**
		 * {@inheritDoc}
		 */
		public boolean isEnabled(ToadletContext toadletContext) {
			return true;
		}
	}

}
