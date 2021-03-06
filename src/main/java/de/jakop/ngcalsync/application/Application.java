/**
 * Copyright © 2012, Frank Jakop
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.jakop.ngcalsync.application;

import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Observable;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.lang3.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.SchedulerException;

import de.jakop.ngcalsync.filter.EventTypeFilter;
import de.jakop.ngcalsync.filter.ICalendarEventFilter;
import de.jakop.ngcalsync.google.GoogleCalendarDaoFactory;
import de.jakop.ngcalsync.google.IGoogleCalendarDAO;
import de.jakop.ngcalsync.i18n.LocalizedUserStrings.UserMessage;
import de.jakop.ngcalsync.notes.INotesCalendarDAO;
import de.jakop.ngcalsync.notes.NotesCalendarDaoFactory;
import de.jakop.ngcalsync.obfuscator.DefaultCalendarEventObfuscator;
import de.jakop.ngcalsync.obfuscator.ICalendarEventObfuscator;
import de.jakop.ngcalsync.service.SyncService;
import de.jakop.ngcalsync.settings.Settings;
import de.jakop.ngcalsync.tray.SynchronizeState;

/**
 * This is the main application class.
 *
 * @author fjakop
 *
 */
public class Application extends Observable {

	private final Log log = LogFactory.getLog(getClass());

	private final Settings settings;
	private final SyncService service;
	private final SchedulerFacade scheduler;
	private final NotesCalendarDaoFactory notesCalendarDaoFactory;
	private final GoogleCalendarDaoFactory googleCalendarDaoFactory;

	/**
	 *
	 * @param settings
	 * @param service
	 * @param notesCalendarDaoFactory
	 * @param googleCalendarDaoFactory
	 */
	public Application(final Settings settings, final SyncService service, final NotesCalendarDaoFactory notesCalendarDaoFactory,
			final GoogleCalendarDaoFactory googleCalendarDaoFactory) {
		Validate.notNull(settings);
		Validate.notNull(service);
		Validate.notNull(notesCalendarDaoFactory);
		Validate.notNull(googleCalendarDaoFactory);
		this.settings = settings;
		this.service = service;
		this.notesCalendarDaoFactory = notesCalendarDaoFactory;
		this.googleCalendarDaoFactory = googleCalendarDaoFactory;

		try {
			scheduler = new SchedulerFacade(this);
		} catch (final ParseException e) {
			throw new RuntimeException(e);
		} catch (final SchedulerException e) {
			throw new RuntimeException(e);
		}

	}


	SchedulerFacade getScheduler() {
		return scheduler;
	}

	/**
	 * Starts synchronisation.
	 */
	void synchronize() {

		try {
			setChanged();
			notifyObservers(SynchronizeState.RUNNING);

			log.info(UserMessage.get().MSG_SYNC_STARTED());

			final ICalendarEventFilter typeFilter = new EventTypeFilter(settings.getSyncAppointmentTypes());
			final ICalendarEventObfuscator typeObfuscator = new DefaultCalendarEventObfuscator(settings.getPrivacySettings());

			final ICalendarEventFilter[] filters = new ICalendarEventFilter[] { typeFilter };
			final ICalendarEventObfuscator[] obfuscators = new ICalendarEventObfuscator[] { typeObfuscator };

			final INotesCalendarDAO notesCalendarDao = notesCalendarDaoFactory.createNotesCalendarDao(settings);
			final IGoogleCalendarDAO googleCalendarDao = googleCalendarDaoFactory.createGoogleCalendarDao(settings);

			service.executeSync(notesCalendarDao, googleCalendarDao, filters, obfuscators, settings);

			// Update Last Sync Execution Date & Time
			settings.setSyncLastDateTime(Calendar.getInstance());
			settings.saveLastSyncDateTime();

			log.info(UserMessage.get().MSG_SYNC_ENDED());
		} finally {
			setChanged();
			notifyObservers(SynchronizeState.IDLE);
		}

	}

	/**
	 * @return <code>true</code>, if a restart is supposed
	 */
	boolean reloadSettings() {

		try {
			return settings.load();
		} catch (final ConfigurationException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

}
