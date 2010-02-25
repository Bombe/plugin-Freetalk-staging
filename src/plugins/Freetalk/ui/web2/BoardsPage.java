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
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.ui.web2.page.Page;

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
	 * @param webInterface
	 *            The web interface
	 */
	public BoardsPage(Template template, WebInterface webInterface) {
		super("Boards", template, "Page.Boards.Title", webInterface);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTemplate(Request request, Template template) {
		FTOwnIdentity ownIdentity = webInterface.getOwnIdentity(request);
		MessageManager messageManager = webInterface.getFreetalkPlugin().getMessageManager();
		Iterator<Board> boardIterator = messageManager.boardIteratorSortedByName();
		Collection<Board> boards = new ArrayList<Board>();
		while (boardIterator.hasNext()) {
			Board board = boardIterator.next();
			try {
				SubscribedBoard subscribedBoard = messageManager.getSubscription(ownIdentity, board.getName());
				boards.add(subscribedBoard);
			} catch (NoSuchBoardException nsbe1) {
				boards.add(board);
			}
		}

		template.set("boards", boards);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getRedirectTarget(Request request) {
		if (webInterface.getOwnIdentity(request) == null) {
			return "LogIn";
		}
		return super.getRedirectTarget(request);
	}

}
