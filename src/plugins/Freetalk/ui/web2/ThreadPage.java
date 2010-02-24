/*
 * Freetalk - ThreadPage.java - Copyright © 2010 David Roden
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

import net.pterodactylus.util.template.Template;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardReplyLink;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.SubscribedBoard.MessageReference;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.ui.web2.page.Page;
import freenet.l10n.BaseL10n;

/**
 * {@link Page} implementation that renders a single thread.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class ThreadPage extends FreetalkTemplatePage {

	/** The currently logged in identity. */
	private FTOwnIdentity ownIdentity;

	/** The board that is being browsed. */
	private SubscribedBoard board;

	/** The thread that is being displayed. */
	private BoardThreadLink thread;

	/**
	 * Creates a new thread page.
	 *
	 * @param template
	 *            The template to render
	 * @param l10n
	 *            The l10n handler
	 * @param webInterface
	 *            The web interface
	 */
	public ThreadPage(Template template, BaseL10n l10n, WebInterface webInterface) {
		super("Thread", template, l10n, "Page.Thread.Title", webInterface);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processTemplate(Request request, Template template) {
		template.set("board", board);
		template.set("thread", thread);
		Collection<MessageReference> messages = new ArrayList<MessageReference>();
		messages.add(thread);
		for (BoardReplyLink reply : board.getAllThreadReplies(thread.getThreadID(), true)) {
			messages.add(reply);
		}
		template.set("messages", messages);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getRedirectTarget(Request request) {
		ownIdentity = webInterface.getOwnIdentity(request);
		if (ownIdentity == null) {
			return "LogIn";
		}
		String boardName = request.getHttpRequest().getParam("Board");
		try {
			board = webInterface.getFreetalkPlugin().getMessageManager().getSubscription(ownIdentity, boardName);
		} catch (NoSuchBoardException nsbe1) {
			return "InvalidBoard";
		}
		String threadId = request.getHttpRequest().getParam("Thread");
		try {
			thread = board.getThreadLink(threadId);
		} catch (NoSuchMessageException nsme1) {
			return "InvalidThread";
		}
		return super.getRedirectTarget(request);
	}

}
