/*
 * Freetalk - BoardsPage.java - Copyright © 2010 David Roden
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
import plugins.Freetalk.Board;
import plugins.Freetalk.ui.web2.page.Page;
import freenet.l10n.BaseL10n;

/**
 * {@link Page} implementation that displays all currently known {@link Board}s.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class BoardsPage extends FreetalkTemplatePage {

	/**
	 * Creates a new page that shows all currently known boards
	 *
	 * @param template
	 *            The template to render
	 * @param l10n
	 *            The l10n handler
	 * @param webInterface
	 *            The web interface
	 */
	public BoardsPage(Template template, BaseL10n l10n, WebInterface webInterface) {
		super("Boards", template, l10n, "Page.Boards.Title", webInterface);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTemplate(Request request, Template template) {
		Iterator<Board> boardIterator = webInterface.getFreetalkPlugin().getMessageManager().boardIteratorSortedByName();
		Collection<Board> boards = new ArrayList<Board>();
		while (boardIterator.hasNext()) {
			boards.add(boardIterator.next());
		}

		template.set("boards", boards);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getRedirectTarget(Request request) {
		if (getOwnIdentity(request) == null) {
			return "LogIn";
		}
		return super.getRedirectTarget(request);
	}

}