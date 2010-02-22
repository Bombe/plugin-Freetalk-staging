/*
 * Freetalk - L10nFilter.java - Copyright © 2010 David Roden
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

package plugins.Freetalk.ui.web;

import java.util.Map;

import net.pterodactylus.util.template.Filter;
import freenet.l10n.BaseL10n;

/**
 * {@link Filter} implementation that replaces a hardcoded l10n identifier with
 * its translated text.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class L10nFilter implements Filter {

	/** The l10n handler. */
	private final BaseL10n l10n;

	/**
	 * Creates a new l10n filter.
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
	public String format(Object data, Map<String, String> parameters) {
		return l10n.getString(String.valueOf(data));
	}

}
