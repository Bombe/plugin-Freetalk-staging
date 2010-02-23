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
import net.pterodactylus.util.template.DefaultTemplateFactory;
import net.pterodactylus.util.template.Filter;
import net.pterodactylus.util.template.Template;
import net.pterodactylus.util.template.TemplateFactory;
import plugins.Freetalk.Board;
import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.SubscribedBoard;
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

	/**
	 * Creates a new L10n template factory.
	 *
	 * @param l10n
	 *            The L10n handler
	 */
	public FreetalkTemplateFactory(BaseL10n l10n) {
		this(DefaultTemplateFactory.getInstance(), l10n);
	}

	/**
	 * Creates a new L10n template factory, retrieving templates from the given
	 * template factory, then adding the {@link L10nFilter} as filter “l10n” to
	 * them.
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
		public String format(Template template, Object data, Map<String, String> parameters) {
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
		public String format(Template template, Object data, Map<String, String> parameters) {
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
		public Object get(Template template, Object object, String member) {
			FTIdentity identity = (FTIdentity) object;
			if ("id".equals(member)) {
				return identity.getID();
			} else if ("name".equals(member)) {
				return identity.getNickname();
			} else if ("unique-name".equals(member)) {
				return identity.getShortestUniqueName(100);
			} else if ("request-uri".equals(member)) {
				return identity.getRequestURI();
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
		public Object get(Template template, Object object, String member) {
			Board board = (Board) object;
			if ("id".equals(member)) {
				return board.getID();
			} else if ("name".equals(member)) {
				return board.getName();
			} else if ("description".equals(member)) {
				return board.getDescription((FTOwnIdentity) template.getData(template, "loggedInUser"));
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
		public Object get(Template template, Object object, String member) {
			SubscribedBoard subscribedBoard = (SubscribedBoard) object;
			if ("message-count".equals(member)) {
				return subscribedBoard.messageCount();
			} else if ("unread-message-count".equals(member)) {
				return subscribedBoard.getUnreadMessageCount();
			}
			return super.get(template, object, member);
		}

	}

}
