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

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import net.pterodactylus.util.template.Template;
import net.pterodactylus.util.template.TemplateFactory;
import plugins.Freetalk.Freetalk;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.ToadletContainer;
import freenet.clients.http.ToadletContext;
import freenet.l10n.BaseL10n;

/**
 * TODO
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class WebInterface {

	private final Freetalk freetalkPlugin;
	private final List<PageToadlet> pageToadlets = new ArrayList<PageToadlet>();

	public WebInterface(Freetalk freetalkPlugin) {
		this.freetalkPlugin = freetalkPlugin;
		registerToadlets();
	}

	//
	// ACTIONS
	//

	public void terminate() {
		unregisterToadlets();
	}

	//
	// PRIVATE ACTIONS
	//

	private void registerToadlets() {
		BaseL10n l10n = freetalkPlugin.getBaseL10n();
		TemplateFactory templateFactory = new FreetalkTemplateFactory(l10n);

		Template welcomeTemplate = templateFactory.createTemplate(createReader("/plugins/Freetalk/ui/web/html/LogIn.html"));
		welcomeTemplate.set("formPassword", freetalkPlugin.getPluginRespirator().getToadletContainer().getFormPassword());

		PageToadletFactory pageToadletFactory = new PageToadletFactory(freetalkPlugin.getPluginRespirator().getHLSimpleClient(), "/Freetalk/");
		pageToadlets.add(pageToadletFactory.createPageToadlet(new LogInPage(welcomeTemplate, l10n, freetalkPlugin.getIdentityManager()), "LogIn"));

		ToadletContainer toadletContainer = freetalkPlugin.getPluginRespirator().getToadletContainer();
		toadletContainer.getPageMaker().addNavigationCategory("/Freetalk/LogIn", "Navigation.Menu.Name", "Navigation.Menu.Tooltip", freetalkPlugin);
		for (PageToadlet toadlet : pageToadlets) {
			String menuName = toadlet.getMenuName();
			if (menuName != null) {
				toadletContainer.register(toadlet, "Navigation.Menu.Name", toadlet.path(), true, "Navigation.Menu.Item." + menuName + ".Name", "Navigation.Menu.Item." + menuName + ".Tooltip", false, new AlwaysEnabledCallback());
			} else {
				toadletContainer.register(toadlet, null, toadlet.path(), true, false);
			}
		}
	}

	private void unregisterToadlets() {
		ToadletContainer toadletContainer = freetalkPlugin.getPluginRespirator().getToadletContainer();
		for (PageToadlet pageToadlet : pageToadlets) {
			toadletContainer.unregister(pageToadlet);
		}
		toadletContainer.getPageMaker().removeNavigationCategory("Navigation.Menu.Name");
	}

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