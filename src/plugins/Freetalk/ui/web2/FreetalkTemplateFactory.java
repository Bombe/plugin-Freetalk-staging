/*
 * shortener - L10nTemplateFactory.java - Copyright © 2010 David Roden
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

import java.io.Reader;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

import net.pterodactylus.util.template.Accessor;
import net.pterodactylus.util.template.DataProvider;
import net.pterodactylus.util.template.DefaultTemplateFactory;
import net.pterodactylus.util.template.Filter;
import net.pterodactylus.util.template.Template;
import net.pterodactylus.util.template.TemplateFactory;
import plugins.Freetalk.Board;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Message;
import plugins.Freetalk.SubscribedBoard;
import plugins.Freetalk.SubscribedBoard.BoardThreadLink;
import plugins.Freetalk.SubscribedBoard.MessageReference;
import plugins.Freetalk.WoT.WoTIdentity;
import plugins.Freetalk.WoT.WoTOwnIdentity;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import freenet.l10n.BaseL10n;

/**
 * {@link TemplateFactory} implementation that creates {@link Template}s that
 * have an {@link L10nFilter} added.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class FreetalkTemplateFactory implements TemplateFactory {

	/** The base template factory. */
	private final TemplateFactory templateFactory;

	/** The L10n filter. */
	private final L10nFilter l10nFilter;

	/** The date filter. */
	private final DateFilter dateFilter;

	/** Accessor for {@link FTIdentity identities}. */
	private final Accessor identityAccessor;

	/** Accesor for {@link Board boards}. */
	private final Accessor boardAccessor;

	/** Accessor for {@link SubscribedBoard subscribed boards}. */
	private final Accessor subscribedBoardAccessor;

	/** Accessor for {@link MessageReference message references}. */
	private final Accessor messageReferenceAccessor;

	/** Accessor for {@link BoardThreadLink}.s */
	private final Accessor boardThreadLinkAccessor;

	/** Accessor for {@link Message messages}. */
	private final Accessor messageAccessor;

	/**
	 * Creates a new Freetalk template factory.
	 *
	 * @param l10n
	 *            The L10n handler
	 */
	public FreetalkTemplateFactory(BaseL10n l10n) {
		this(DefaultTemplateFactory.getInstance(), l10n);
	}

	/**
	 * Creates a new Freetalk template factory, retrieving templates from the
	 * given template factory, then adding all filters used by Freetalk to them.
	 *
	 * @param templateFactory
	 *            The base template factory
	 * @param l10n
	 *            The L10n handler
	 */
	public FreetalkTemplateFactory(TemplateFactory templateFactory, BaseL10n l10n) {
		this.templateFactory = templateFactory;
		this.l10nFilter = new L10nFilter(l10n);
		this.dateFilter = new DateFilter();
		this.identityAccessor = new IdentityAccessor();
		this.boardAccessor = new BoardAccessor();
		this.subscribedBoardAccessor = new SubscribedBoardAccessor();
		this.messageReferenceAccessor = new MessageReferenceAccessor();
		this.boardThreadLinkAccessor = new BoardThreadLinkAccessor();
		this.messageAccessor = new MessageAccessor();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Template createTemplate(Reader templateSource) {
		Template template = templateFactory.createTemplate(templateSource);
		template.addFilter("l10n", l10nFilter);
		template.addFilter("date", dateFilter);
		template.addAccessor(FTIdentity.class, identityAccessor);
		template.addAccessor(SubscribedBoard.class, subscribedBoardAccessor);
		template.addAccessor(Board.class, boardAccessor);
		template.addAccessor(BoardThreadLink.class, boardThreadLinkAccessor);
		template.addAccessor(MessageReference.class, messageReferenceAccessor);
		template.addAccessor(Message.class, messageAccessor);
		return template;
	}

	/**
	 * {@link Filter} implementation replaces {@link String} values with their
	 * translated equivalents.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public static class L10nFilter implements Filter {

		/** The l10n handler. */
		private final BaseL10n l10n;

		/**
		 * Creates a new L10n filter.
		 *
		 * @param l10n
		 *            The l10n handler
		 */
		public L10nFilter(BaseL10n l10n) {
			this.l10n = l10n;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String format(DataProvider dataProvider, Object data, Map<String, String> parameters) {
			return l10n.getString(String.valueOf(data));
		}

	}

	/**
	 * {@link Filter} implementation that formats a date. The date may be given
	 * either as a {@link Date} or a {@link Long} object.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public static class DateFilter implements Filter {

		/** The date formatter. */
		private final DateFormat dateFormat = DateFormat.getInstance();

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String format(DataProvider dataProvider, Object data, Map<String, String> parameters) {
			if (data instanceof Date) {
				return dateFormat.format((Date) data);
			} else if (data instanceof Long) {
				return dateFormat.format(new Date((Long) data));
			}
			return "";
		}

	}

	/**
	 * {@link Accessor} implementation that returns sensible values for the
	 * “id”, “name”, and “request-uri” members of an {@link FTIdentity}.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public class IdentityAccessor implements Accessor {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object get(DataProvider dataProvider, Object object, String member) {
			FTIdentity identity = (FTIdentity) object;
			if ("id".equals(member)) {
				return identity.getID();
			} else if ("name".equals(member)) {
				return identity.getNickname();
			} else if ("unique-name".equals(member)) {
				return identity.getShortestUniqueName(100);
			} else if ("request-uri".equals(member)) {
				return identity.getRequestURI();
			} else if ("post-count".equals(member)) {
				return ((WebInterface) dataProvider.getData("webInterface")).getFreetalkPlugin().getMessageManager().getMessagesBy(identity).size();
			} else if ("truster-count".equals(member)) {
				try {
					return ((WebInterface) dataProvider.getData("webInterface")).getFreetalkPlugin().getIdentityManager().getReceivedTrustsCount(identity, 1);
				} catch (Exception e1) {
					/* ignore, fall through. */
				}
			} else if ("distruster-count".equals(member)) {
				try {
					return ((WebInterface) dataProvider.getData("webInterface")).getFreetalkPlugin().getIdentityManager().getReceivedTrustsCount(identity, -1);
				} catch (Exception e1) {
					/* ignore, fall through. */
				}
			} else if ("trustee-count".equals(member)) {
				try {
					return ((WebInterface) dataProvider.getData("webInterface")).getFreetalkPlugin().getIdentityManager().getGivenTrustsCount(identity, 1);
				} catch (Exception e1) {
					/* ignore, fall through. */
				}
			} else if ("distrustee-count".equals(member)) {
				try {
					return ((WebInterface) dataProvider.getData("webInterface")).getFreetalkPlugin().getIdentityManager().getGivenTrustsCount(identity, -1);
				} catch (Exception e1) {
					/* ignore, fall through. */
				}
			} else if ("local-trust".equals(member)) {
				FTOwnIdentity ownIdentity = (FTOwnIdentity) dataProvider.getData("loggedInUser");
				try {
					return ((WoTOwnIdentity) ownIdentity).getTrustIn((WoTIdentity) identity);
				} catch (NotInTrustTreeException nitte1) {
					/* ignore, fall through. */
				} catch (Exception e) {
					/* kick somebody. */
				}
			} else if ("score".equals(member)) {
				FTOwnIdentity ownIdentity = (FTOwnIdentity) dataProvider.getData("loggedInUser");
				try {
					return ((WoTOwnIdentity) ownIdentity).getScoreFor((WoTIdentity) identity);
				} catch (NotInTrustTreeException nitte1) {
					/* ignore, fall through. */
				} catch (Exception e) {
					/* kick somebody. */
				}
			}
			return null;
		}

	}

	/**
	 * {@link Accessor} implementation that can handle the “index”, “message”,
	 * “read”, and “date” properties of a {@link MessageReference}.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public class MessageReferenceAccessor implements Accessor {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object get(DataProvider dataProvider, Object object, String member) {
			MessageReference messageReference = (MessageReference) object;
			if ("index".equals(member)) {
				return messageReference.getIndex();
			} else if ("message".equals(member)) {
				try {
					return messageReference.getMessage();
				} catch (MessageNotFetchedException mnfe1) {
					/* ignore, just return null. */
				}
			} else if ("read".equals(member)) {
				return messageReference.wasRead();
			} else if ("date".equals(member)) {
				return messageReference.getMessageDate();
			}
			return null;
		}

	}

	/**
	 * {@link Accessor} implementation that can handle the “index”, “message”,
	 * “read”, and “date” properties of a {@link MessageReference}.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public class BoardThreadLinkAccessor extends MessageReferenceAccessor {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object get(DataProvider dataProvider, Object object, String member) {
			BoardThreadLink boardThreadLink = (BoardThreadLink) object;
			if ("last-reply-date".equals(member)) {
				return boardThreadLink.getLastReplyDate();
			} else if ("id".equals(member)) {
				return boardThreadLink.getThreadID();
			} else if ("reply-count".equals(member)) {
				SubscribedBoard board = (SubscribedBoard) dataProvider.getData("board");
				return board.threadReplyCount(boardThreadLink.getThreadID());
			} else if ("unread-reply-count".equals(member)) {
				SubscribedBoard board = (SubscribedBoard) dataProvider.getData("board");
				return board.threadUnreadReplyCount(boardThreadLink.getThreadID());
			} else if ("read".equals(member)) {
				return boardThreadLink.wasThreadRead();
			}
			return super.get(dataProvider, object, member);
		}

	}

	/**
	 * {@link Accessor} implementation that exposes various properties of a
	 * {@link Message} object.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public class MessageAccessor implements Accessor {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object get(DataProvider dataProvider, Object object, String member) {
			Message message = (Message) object;
			if ("id".equals(member)) {
				return message.getID();
			} else if ("author".equals(member)) {
				return message.getAuthor();
			} else if ("title".equals(member)) {
				return message.getTitle();
			} else if ("text".equals(member)) {
				return message.getText();
			} else if ("date".equals(member)) {
				return message.getDate();
			} else if ("fetch-date".equals(member)) {
				return message.getFetchDate();
			}
			return null;
		}

	}

	/**
	 * {@link Accessor} implementation that exposes the {@link Board#getID “id”}
	 * , {@link Board#getName() “name”}, and {@link Board#getFirstSeenDate()
	 * “first-seen-date”} properties of a {@link Board}.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public class BoardAccessor implements Accessor {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object get(DataProvider dataProvider, Object object, String member) {
			Board board = (Board) object;
			if ("id".equals(member)) {
				return board.getID();
			} else if ("name".equals(member)) {
				return board.getName();
			} else if ("description".equals(member)) {
				return board.getDescription((FTOwnIdentity) dataProvider.getData("loggedInUser"));
			} else if ("first-seen-date".equals(member)) {
				return board.getFirstSeenDate();
			}
			return null;
		}

	}

	/**
	 * {@link Accessor} implementation that can, in addition to
	 * {@link BoardAccessor}, expose more properties.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public class SubscribedBoardAccessor extends BoardAccessor {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object get(DataProvider dataProvider, Object object, String member) {
			SubscribedBoard subscribedBoard = (SubscribedBoard) object;
			if ("message-count".equals(member)) {
				return subscribedBoard.messageCount();
			} else if ("unread-message-count".equals(member)) {
				return subscribedBoard.getUnreadMessageCount();
			} else if ("latest-message-reference".equals(member)) {
				try {
					return subscribedBoard.getLatestMessage();
				} catch (NoSuchMessageException nsme1) {
					/* just ignore. */
				}
			} else if ("subscribed".equals(member)) {
				return true;
			}
			return super.get(dataProvider, object, member);
		}

	}

}
