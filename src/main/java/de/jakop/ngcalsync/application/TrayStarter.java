package de.jakop.ngcalsync.application;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import de.jakop.ngcalsync.Constants;
import de.jakop.ngcalsync.StartApplication;
import de.jakop.ngcalsync.i18n.LocalizedTechnicalStrings.TechMessage;
import de.jakop.ngcalsync.i18n.LocalizedUserStrings.UserMessage;
import de.jakop.ngcalsync.oauth.GuiReceiver;
import de.jakop.ngcalsync.settings.PreferencesComposite;
import de.jakop.ngcalsync.settings.Settings;
import de.jakop.ngcalsync.util.StatefulTrayIcon;
import de.jakop.ngcalsync.util.StatefulTrayIcon.State;
import de.jakop.ngcalsync.util.logging.CompositeAppenderLog4J;

/**
 * Starts the application without immediate synchronisation and moves it to the system tray
 * where the user can produce a popup menu for synchronisation, exit, log etc.
 * 
 * @author fjakop
 *
 */
public class TrayStarter implements IApplicationStarter {

	private final Log log = LogFactory.getLog(getClass());
	private StatefulTrayIcon icon;
	private Future<Void> synchronizing;

	@Override
	public void startApplication(final Application application, final Settings settings) {
		log.debug(TechMessage.get().MSG_START_IN_TRAY_MODE());

		final Display display = new Display();
		final Shell shell = new Shell(display);

		settings.setUserInputReceiver(new GuiReceiver(shell));
		createUI(shell, application, settings);


		// Create and check the event loop
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		display.dispose();

	}

	private void synchronize(final Shell parent, final Application application) {
		if (synchronizing != null && !synchronizing.isDone()) {
			log.warn(UserMessage.get().MSG_SYNC_IN_PROGRESS());
			return;
		}
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		synchronizing = executor.submit(new SynchronizeCallable(parent, application));
	}

	private void createUI(final Shell parent, final Application application, final Settings settings) {

		// Create a pop-up menu and its components
		final Menu popup = new Menu(parent, SWT.POP_UP);

		createSyncUI(popup, parent, application);
		createLogViewUI(popup);
		createAboutUI(popup);
		createSettingsUI(popup, settings);
		createExitUI(popup, parent);

		// put it into a tray item
		createTrayItem(parent, application, popup);

	}

	private void createTrayItem(final Shell parent, final Application application, final Menu popup) {

		final Tray tray = parent.getDisplay().getSystemTray();
		final TrayItem trayItem = new TrayItem(tray, SWT.NONE);

		try {
			icon = new StatefulTrayIcon(trayItem);
			icon.setState(State.NORMAL);
		} catch (final IOException e) {
			log.error(TechMessage.get().MSG_TRAY_ICON_NOT_LOADABLE(), e);
		}

		trayItem.addListener(SWT.MenuDetect, new Listener() {
			@Override
			public void handleEvent(final Event arg0) {
				popup.setVisible(true);
			}
		});

		trayItem.addListener(SWT.DefaultSelection, new Listener() {
			@Override
			public void handleEvent(final Event arg0) {
				synchronize(parent, application);
			}
		});
	}

	private void createExitUI(final Menu popup, final Shell parent) {
		final MenuItem exitItem = createMenuItem(popup, UserMessage.get().MENU_ITEM_EXIT());

		exitItem.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(final SelectionEvent arg0) {
				widgetSelected(arg0);
			}

			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				parent.dispose();
			}
		});
	}

	private void createSyncUI(final Menu popup, final Shell parent, final Application application) {

		final MenuItem syncItem = createMenuItem(popup, UserMessage.get().MENU_ITEM_SYNCHRONIZE());

		syncItem.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(final SelectionEvent arg0) {
				widgetSelected(arg0);
			}

			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				synchronize(parent, application);
			}
		});

	}

	private void createAboutUI(final Menu popup) {

		final Shell aboutShell = createShell(UserMessage.get().TITLE_ABOUT_WINDOW());

		final Browser aboutViewer = new Browser(aboutShell, SWT.NONE);
		aboutViewer.setText(getApplicationInformation());

		final MenuItem aboutItem = createMenuItem(popup, UserMessage.get().MENU_ITEM_ABOUT());

		aboutItem.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(final SelectionEvent arg0) {
				widgetSelected(arg0);
			}

			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				aboutShell.open();
			}
		});

	}

	private void createLogViewUI(final Menu popup) {

		final Shell logShell = createShell(UserMessage.get().TITLE_SYNC_LOG_WINDOW());

		final CompositeAppenderLog4J appender = new CompositeAppenderLog4J(logShell, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		appender.setLayout(new PatternLayout("%5p - %m%n"));
		final Logger rootLogger = Logger.getRootLogger();
		rootLogger.addAppender(appender);
		rootLogger.setLevel(Level.INFO);

		final MenuItem logItem = createMenuItem(popup, UserMessage.get().MENU_ITEM_SHOW_LOG());

		logItem.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetDefaultSelected(final SelectionEvent arg0) {
				widgetSelected(arg0);
			}

			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				logShell.open();
			}
		});

	}

	private void createSettingsUI(final Menu popup, final Settings settings) {

		final Shell settingsShell = createShell(UserMessage.get().TITLE_ABOUT_WINDOW());

		try {
			settings.load();
		} catch (final ConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		new PreferencesComposite(settingsShell, SWT.V_SCROLL | SWT.H_SCROLL, settings);

		final MenuItem settingsItem = createMenuItem(popup, "Settings");

		settingsItem.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(final SelectionEvent arg0) {
				widgetSelected(arg0);
			}

			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				settingsShell.open();
			}
		});
	}

	private Shell createShell(final String title) {

		final Shell shell = new Shell(SWT.RESIZE | SWT.DIALOG_TRIM);

		// do not dispose shell on closing the window(s)
		shell.addShellListener(new ShellAdapter() {
			@Override
			public void shellClosed(final ShellEvent e) {
				shell.setVisible(false);
				e.doit = false;
			}
		});

		shell.setText(title);
		shell.setImage(new Image(shell.getDisplay(), getClass().getResourceAsStream(Constants.ICON_NORMAL)));
		shell.setLayout(new FillLayout());
		shell.setSize(700, 500);
		return shell;
	}

	private MenuItem createMenuItem(final Menu parent, final String text) {
		final MenuItem item = new MenuItem(parent, SWT.PUSH);
		item.setText(text);
		return item;
	}

	private String getApplicationInformation() {
		final StringBuilder builder = new StringBuilder();
		builder.append("<b>").append(Constants.APPLICATION_NAME).append("</b>").append("<p>");
		builder.append(UserMessage.get().APPLICATION_DESCRIPTION()).append("<p>");
		builder.append("Version ").append(StartApplication.class.getPackage().getImplementationVersion()).append("<p>");
		builder.append("").append("<br/>");
		String license;
		try {
			license = IOUtils.toString(getClass().getResourceAsStream("/LICENSE"));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		builder.append(license.replaceAll("\n", "<br/>")).append("<br/>");

		return builder.toString();
	}
	private class SynchronizeCallable implements Callable<Void> {

		private final Shell parent;
		private final Application application;

		public SynchronizeCallable(final Shell parent, final Application application) {
			this.parent = parent;
			this.application = application;
		}

		@Override
		public Void call() throws Exception {
			try {
				if (application.reloadSettings()) {
					parent.getDisplay().syncExec(new Runnable() {
						@Override
						public void run() {
							MessageDialog.open(MessageDialog.INFORMATION, parent, UserMessage.get().TITLE_INPUT_REQUESTED(), UserMessage.get().MSG_CONFIGURATION_UPGRADED(), SWT.BORDER);
						}
					});
					return null;
				}
				icon.setState(State.BLINK);
				application.synchronize();
				icon.setState(State.NORMAL);
			} catch (final Exception ex) {
				log.error(ExceptionUtils.getStackTrace(ex));
				final String home = System.getenv("user.home");
				final File logfile = new File(home, "ngcalsync.log");
				parent.getDisplay().syncExec(new Runnable() {
					@Override
					public void run() {
						// TODO window title
						MessageDialog.open(MessageDialog.ERROR, parent, "", UserMessage.get().MSG_SYNC_FAILED(logfile.getAbsolutePath()), SWT.BORDER);
					}
				});
			}
			return null;
		}
	}




}
