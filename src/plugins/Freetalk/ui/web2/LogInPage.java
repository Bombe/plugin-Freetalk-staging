/*
 * Freetalk - WelcomePage.java - Copyright © 2010 David Roden
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.pterodactylus.util.template.Template;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.IdentityManager;
import freenet.l10n.BaseL10n;

/**
 * Renders the log-in page.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class LogInPage extends FreetalkTemplatePage {

	/** The identity manager. */
	private final IdentityManager identityManager;

	/**
	 * Creates a new log-in page.
	 *
	 * @param template
	 *            The template to render
	 * @param l10n
	 *            The L10n handler
	 * @param webInterface
	 *            The web interface
	 */
	public LogInPage(Template template, BaseL10n l10n, WebInterface webInterface) {
		super("LogIn", template, l10n, "Page.LogIn.Title", webInterface);
		this.identityManager = webInterface.getFreetalkPlugin().getIdentityManager();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTemplate(Template template) {
		Collection<FTOwnIdentity> ownIdentities = new ArrayList<FTOwnIdentity>();
		for (Iterator<? extends FTOwnIdentity> ownIdentityIterator = identityManager.ownIdentityIterator(); ownIdentityIterator.hasNext();) {
			FTOwnIdentity ownIdentity = ownIdentityIterator.next();
			ownIdentities.add(ownIdentity);
		}
		template.set("ownIdentities", ownIdentities);
	}

}
