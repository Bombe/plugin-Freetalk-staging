/*
 * Freetalk - BoardPage.java - Copyright © 2010 David Roden
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

import java.util.Collection;

import net.pterodactylus.util.template.Template;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.ui.web2.page.Page;
import freenet.l10n.BaseL10n;

/**
 * {@link Page} that lists all messages in a board.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class BoardPage extends FreetalkTemplatePage {

	/**
	 * Creates a new board page.
	 *
	 * @param template
	 *            The template to render
	 * @param l10n
	 *            The L10n handler
	 * @param webInterface
	 *            The web interface
	 */
	public BoardPage(Template template, BaseL10n l10n, WebInterface webInterface) {
		super("Board", template, l10n, "Page.Board.Title", webInterface);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTemplate(Request request, Template template) {
		String boardName = request.getHttpRequest().getParam("Name");
		MessageManager messageManager = webInterface.getFreetalkPlugin().getMessageManager();
		FTOwnIdentity ownIdentity = webInterface.getOwnIdentity(request);
		try {
			SubscribedBoard board = messageManager.getSubscription(ownIdentity, boardName);
			Collection<BoardThreadLink> boardThreads = board.getThreads();
			template.set("loggedInUser", webInterface.getOwnIdentity(request));
			template.set("board", board);
			template.set("threads", boardThreads);
		} catch (NoSuchBoardException nsbe1) {
			throw new RuntimeException("Could not find board: " + boardName, nsbe1);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getRedirectTarget(Request request) {
		if (webInterface.getOwnIdentity(request) == null) {
			return "LogIn";
		}
		String boardName = request.getHttpRequest().getParam("Name");
		if ("".equals(boardName)) {
			return "InvalidBoard";
		}
		return super.getRedirectTarget(request);
	}

}
