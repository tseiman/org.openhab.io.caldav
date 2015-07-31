/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.caldav.internal;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.meta.When;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.component.VEvent;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.LongRange;
import org.joda.time.DateTime;
import org.openhab.core.service.AbstractActiveService;
import org.openhab.io.caldav.internal.util.ExecuteCommandJob;
import org.openhab.io.caldav.internal.util.TimeRangeCalendar;
import org.openhab.io.caldav.util.EasySSLProtocolSocketFactory;
import org.osaf.caldav4j.CalDAVCollection;
import org.osaf.caldav4j.CalDAVConstants;
import org.osaf.caldav4j.exceptions.CalDAV4JException;
import org.osaf.caldav4j.methods.CalDAV4JMethodFactory;
import org.osaf.caldav4j.methods.HttpClient;
import org.osaf.caldav4j.model.request.CalendarQuery;
import org.osaf.caldav4j.util.GenerateQuery;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




/**
 * Service which downloads Calendar events, parses their content and creates
 * Quartz-jobs and triggers out of them.
 * 
 * this code is derived from GCal implementation written by Thomas.Eichstaedt-Engelen
 * @author Thomas.Schmidt
 * @since 1.8.0
 */
public class CalDavEventDownloader extends AbstractActiveService implements ManagedService {

	private static final String CALDAV_SCHEDULER_GROUP = "caldav";

	private static final Logger logger = LoggerFactory.getLogger(CalDavEventDownloader.class);

	private static String host = "";
	private static int port = 0;
	private static String username = "";
	private static String password = "";
	private static String url = "";
	private static boolean tls = true;
	private static boolean strictTls = true;


	/** holds the current refresh interval, default to 900000ms (15 minutes) */
	public static int refreshInterval = 900000;


	/** holds the local quartz scheduler instance */
	private Scheduler scheduler;


	/**
	 * RegEx to extract the start and end commands from the Calendar-Event content.
	 * (<code>'start\s*?\{(.*?)\}\s*end\s*?\{(.*?)\}\s*'</code>)
	 */
	private static final Pattern EXTRACT_STARTEND_CONTENT = Pattern.compile("start\\s*?\\{(.*?)\\}\\s*end\\s*?\\{(.*?)\\}\\s*", Pattern.DOTALL);

	/**
	 * RegEx to extract the modified by command from the Calendar-Event content.
	 * (<code>'(.*?)modified by\s*?\{(.*?)\}.*'</code>)
	 */
	private static final Pattern EXTRACT_MODIFIEDBY_CONTENT = Pattern.compile("(.*?)modified by\\s*?\\{(.*?)\\}.*", Pattern.DOTALL);


	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	@Override
	protected String getName() {
		return "CalDav Calender Event-Downloader";
	}

	@Override
	public void activate() {
		logger.debug("activate CalDavEventDownloader");
		try {
			scheduler = StdSchedulerFactory.getDefaultScheduler();
			super.activate();
		}
		catch (SchedulerException se) {
			logger.error("initializing scheduler throws exception", se);
		}
	}




	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void execute() {

		List<VEvent> eventList = downloadEventFeed();
		if(! eventList.isEmpty()) {
			logger.debug("found {} calendar events to process", eventList.size());

			try {
				if (scheduler.isShutdown()) {
					logger.warn("Scheduler has been shut down - probably due to exceptions?");
				}
				cleanJobs();
				processEntries(eventList);
			}
			catch (SchedulerException se) {
				logger.error("scheduling jobs throws exception", se);
			}	

		} else {
			logger.debug("caldav contains no events ...");
		}


	}

	/**
	 * Connects to Caldav-Calendar Service and downloads the specified Calendar
	 * <code>url</code>, <code>username</code>, <code>host</code>, <code>port</code> and <code>password</code> are taken
	 * from the corresponding config parameter in <code>openhab.cfg</code>. 
	 * The same is for ssl/tls settings  <code>ssl</code> and 
	 * 
	 * @return the corresponding Calendar-Feed or <code>null</code> if an error
	 * occurs. <i>Note:</i> We do only return events if their startTime lies between
	 * <code>now</code> and <code>now + 2 * refreshInterval</code> to reduce
	 * the amount of events to process.
	 */
	public static List<VEvent> downloadEventFeed() {


		List<VEvent> eventList = new ArrayList<VEvent>();

		if(tls && (! strictTls)) {
			ProtocolSocketFactory socketFactory =   new EasySSLProtocolSocketFactory( );
			Protocol https = new Protocol( "https", socketFactory, 443);
			Protocol.registerProtocol( "https", https );
		}

		HttpClient httpClient = new HttpClient();
		httpClient.getHostConfiguration().setHost(host, port, tls ? "https" : "http");


		UsernamePasswordCredentials httpCredentials = new UsernamePasswordCredentials(username, password);
		httpClient.getState().setCredentials(AuthScope.ANY, httpCredentials);
		httpClient.getParams().setAuthenticationPreemptive(true);



		GenerateQuery gq=new GenerateQuery();
		TimeZone timeZone = TimeZone.getDefault();
		java.util.Calendar calStartSearch = new GregorianCalendar(timeZone);
		//		calStartSearch.add(java.util.Calendar.DAY_OF_MONTH, -1);

		java.util.Calendar calEndSearch = new GregorianCalendar();
		calEndSearch.add(java.util.Calendar.SECOND,  (2* refreshInterval/1000));


		SimpleDateFormat dateSearchFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
		dateSearchFormat.setTimeZone(TimeZone.getTimeZone("UTC"));





		try {
			logger.debug("getting CalDav entries with filter \"VEVENT [{};{}] : STATUS!=CANCELLED\"",  dateSearchFormat.format(calStartSearch.getTime()),dateSearchFormat.format(calEndSearch.getTime())  );

			gq.setFilter("VEVENT [" + dateSearchFormat.format(calStartSearch.getTime()) +";" + dateSearchFormat.format(calEndSearch.getTime()) + "] : STATUS!=CANCELLED");
			CalendarQuery calendarQuery = gq.generate();

			calendarQuery.validate();

			//				Document document = calendarQuery.createNewDocument(XMLUtils.getDOMImplementation());
			//				XMLUtils.toPrettyXML(document);
			//				System.out.println(XMLUtils.toPrettyXML(document));


			CalDAVCollection collection = new CalDAVCollection(
					url,
					(HostConfiguration) httpClient.getHostConfiguration().clone(),
					new CalDAV4JMethodFactory(),
					CalDAVConstants.PROC_ID_DEFAULT
					);


			List<Calendar>calendars = collection.queryCalendars(httpClient, calendarQuery);


			for (Calendar calendar : calendars) {
				ComponentList componentList = calendar.getComponents().getComponents(Component.VEVENT);
				Iterator<?> eventIterator = componentList.iterator();

				while (eventIterator.hasNext()) {

					VEvent ve = (VEvent) eventIterator.next();
					eventList.add(ve);
					logger.debug("Got CalDav entry <{}>, with command <{}> for time interval: {} to  {} ", ve.getSummary(),ve.getDescription(),ve.getStartDate(),ve.getEndDate());						
				}
			}


		} catch (CalDAV4JException ce) {
			logger.error("scheduling jobs throws exception", ce);
		} 


		return eventList;
	}


	/**
	 * Delete all {@link Job}s of the group <code>GCAL_SCHEDULER_GROUP</code>
	 * 
	 * @throws SchedulerException if there is an internal Scheduler error.
	 */
	private void cleanJobs() throws SchedulerException {
		Set<JobKey> jobKeys = 	scheduler.getJobKeys(jobGroupEquals(CALDAV_SCHEDULER_GROUP));
		scheduler.deleteJobs(new ArrayList<JobKey>(jobKeys));
	}

	/**
	 * <p>
	 * Iterates through <code>entries</code>, extracts the event content and
	 * creates quartz calendars, jobs and corresponding triggers for each event.
	 * </p>
	 * <p>
	 * The following steps are done at event processing:
	 * <ul>
	 * <li>find events with empty content</li>
	 * <li>create a {@link TimeRangeCalendar} for each event (unique by title) and add a TimeRange for each {@link When}</li>
	 * <li>add each {@link TimeRangeCalendar} to the {@link Scheduler}</li>
	 * <li>find events with content</li>
	 * <li>add a Job with the corresponding Triggers for each event</li>
	 * </ul> 
	 *  
	 * @param entries the GCalendar events to create quart jobs for. 
	 * @throws SchedulerException if there is an internal Scheduler error.
	 */
	private void processEntries(List<VEvent> entries) throws SchedulerException {
		Map<String, TimeRangeCalendar> calendarCache = new HashMap<String, TimeRangeCalendar>();


		// find all events with empty content - these events are taken to modify
		// the scheduler
		if (entries == null) return;

		for (VEvent event : entries) {
			String eventContent ="";
			String eventTitle = "";

			if(event.getDescription() != null) 
				eventContent = event.getDescription().getValue();

			if(event.getSummary() != null) 
				eventTitle = event.getSummary().getValue();

			if (StringUtils.isBlank(eventContent)) {
				logger.debug("found event '{}' with no content, add this event to the excluded " +
						"TimeRangesCalendar - this event could be referenced by the modifiedBy clause",
						eventTitle);

				if (!calendarCache.containsKey(eventTitle)) {
					calendarCache.put(eventTitle, new TimeRangeCalendar());
				}
				TimeRangeCalendar timeRangeCalendar = calendarCache.get(eventTitle);
				timeRangeCalendar.addTimeRange(new LongRange(event.getStartDate().getDate().getTime(), event.getEndDate().getDate().getTime()));

			}
		}

		// add all calendars to the Scheduler an rebase all existing Triggers
		// the calendars has to be added first, to schedule Triggers successfully
		for (Entry<String, TimeRangeCalendar> entry : calendarCache.entrySet()) {
			scheduler.addCalendar(entry.getKey(), entry.getValue(), true, true);
		}

		// now we process all events with content

		for (VEvent event : entries) {
			String eventContent ="";
			String eventTitle = "";


			if(event.getDescription() != null) 
				eventContent = event.getDescription().getValue();

			if(event.getSummary() != null) 
				eventTitle = event.getSummary().getValue();

			if (StringUtils.isNotBlank(eventContent)) {
				CalendarEventContent cec = parseEventContent(eventContent);

				String modifiedByEvent = null;
				if (calendarCache.containsKey(cec.modifiedByEvent)) {
					modifiedByEvent = cec.modifiedByEvent;
				}

				JobDetail startJob = createJob(cec.startCommands, event, true);
				boolean triggersCreated = 
						createTriggerAndSchedule(startJob, event, modifiedByEvent, true);

				if (triggersCreated) {
					logger.info("created new startJob '{}' with details '{}'", 
							eventTitle, createJobInfo(event, startJob));
				}

				// do only create end-jobs if there are end-commands ...
				if (StringUtils.isNotBlank(cec.endCommands)) {
					JobDetail endJob = createJob(cec.endCommands, event, false);
					triggersCreated = createTriggerAndSchedule(endJob, event, modifiedByEvent, false);

					if (triggersCreated) {
						logger.info("created new endJob '{}' with details '{}'",
								eventTitle, createJobInfo(event, endJob));
					}
				}
			}		
		}
	}


	/**
	 * <p>
	 * Extracts start, end and modified by-commands from <code>content</code>.
	 * Start-Commands will be executed at start-time and End-Commands will be
	 * executed at end-time of the calendar-event. The modified-by command defines
	 * the name of special event which disables the created Job temporarily.
	 * </p><p>
	 * If the RegExp <code>EXTRACT_STARTEND_CONTENT</code> doen't match the
	 * complete content is taken as set of Start-Commands.
	 * </p>
	 * 
	 * @param content the set of Start- and End-Commands
	 * @return the parsed event content
	 */
	protected CalendarEventContent parseEventContent(String content) {
		CalendarEventContent eventContent = new CalendarEventContent();
		String commandContent;
		Matcher modifiedByMatcher = EXTRACT_MODIFIEDBY_CONTENT.matcher(content);
		if (modifiedByMatcher.find()) {
			commandContent = modifiedByMatcher.group(1);
			eventContent.modifiedByEvent = StringUtils.trimToEmpty(modifiedByMatcher.group(2));
		} else {
			commandContent = content;
		}

		Matcher startEndMatcher = EXTRACT_STARTEND_CONTENT.matcher(commandContent);
		if (startEndMatcher.find()) {
			eventContent.startCommands = StringUtils.trimToEmpty(startEndMatcher.group(1));
			eventContent.endCommands = StringUtils.trimToEmpty(startEndMatcher.group(2));
		}
		else {
			eventContent.startCommands = StringUtils.trimToEmpty(commandContent);
			logger.debug("given event content doesn't match regular expression to " +
					"extract start-, end commands - using whole content as startCommand ({})", commandContent);
		}


		return eventContent;
	}

	/**
	 * Creates a new quartz-job with jobData <code>content</code> in the scheduler
	 * group <code>GCAL_SCHEDULER_GROUP</code> if <code>content</code> is not
	 * blank.
	 * 
	 * @param content the set of commands to be executed by the
	 * {@link ExecuteCommandJob} later on
	 * @param event
	 * @param isStartEvent indicator to identify whether this trigger will be
	 * triggering a start or an end command.
	 * 
	 * @return the {@link JobDetail}-object to be used at further processing
	 */
	protected JobDetail createJob(String content, VEvent event, boolean isStartEvent) {


		String jobIdentity = event.getUid() + (isStartEvent ? "_start" : "_end");


		if (StringUtils.isBlank(content)) {
			logger.debug("content of job '" + jobIdentity + "' is empty -> no task will be created!");
			return null;
		}

		JobDetail job = newJob(ExecuteCommandJob.class)
				.usingJobData(ExecuteCommandJob.JOB_DATA_CONTENT_KEY, content)
				.withIdentity(jobIdentity, CALDAV_SCHEDULER_GROUP)
				.build();

		return job;

	} 

	/**
	 * Creates a set quartz-triggers for <code>job</code>. For each {@link When}
	 * object of <code>event</code> a new trigger is created. That is the case
	 * in recurring events where gcal creates one event (with one unique IcalUID)
	 * and a set of {@link When}-object for each occurrence.
	 * 
	 * @param job the {@link Job} to create triggers for
	 * @param event the {@link CalendarEventEntry} to read the {@link When}-objects
	 * from
	 * @param modifiedByEvent defines the name of an event which modifies the
	 * schedule of the new Trigger
	 * @param isStartEvent indicator to identify whether this trigger will be
	 * triggering a start or an end command.
	 * 
	 * @throws SchedulerException if there is an internal Scheduler error.
	 */
	protected boolean createTriggerAndSchedule(JobDetail job, VEvent event, String modifiedByEvent, boolean isStartEvent) {
		boolean triggersCreated = false;

		if (job == null) {
			logger.debug("job is null -> no triggers are created");
			return false;
		}


		String jobIdentity = event.getUid() + (isStartEvent ? "_start" : "_end");



		long dateValue = isStartEvent ? event.getStartDate().getDate().getTime() : event.getEndDate().getDate().getTime();
		/* TODO: TEE: do only create a new trigger when the start/endtime 
		 * lies in the future. This exclusion is necessary because the SimpleTrigger
		 * triggers a job even if the startTime lies in the past. If somebody
		 * knows the way to let quartz ignore such triggers this exclusion
		 * can be omitted. */


		if (dateValue >= DateTime.now().toDate().getTime()) { 

			Trigger trigger;

			if (StringUtils.isBlank(modifiedByEvent)) {
				trigger = newTrigger()
						.forJob(job)
						.withIdentity(jobIdentity + "_" + dateValue + "_trigger", CALDAV_SCHEDULER_GROUP)
						.startAt(new Date(dateValue))
						.build();
			} else {
				trigger = newTrigger()
						.forJob(job)
						.withIdentity(jobIdentity + "_" + dateValue + "_trigger", CALDAV_SCHEDULER_GROUP)
						.startAt(new Date(dateValue))
						.modifiedByCalendar(modifiedByEvent)
						.build();
			}

			try {
				scheduler.scheduleJob(job, trigger);
				triggersCreated = true;
			}
			catch (SchedulerException se) {
				logger.warn("scheduling Trigger '" + trigger + "' throws an exception.", se);
			}


		}
		return triggersCreated;
	}

	/**
	 * Creates a detailed description of a <code>job</code> for logging purpose.
	 * 
	 * @param job the job to create a detailed description for
	 * @return a detailed description of the new <code>job</code>
	 */
	private String createJobInfo(VEvent event, JobDetail job) {
		if (job == null) {
			return "SchedulerJob [null]";
		}

		StringBuffer sb = new StringBuffer();
		sb.append("SchedulerJob [jobKey=").append(job.getKey().getName());
		sb.append(", jobGroup=").append(job.getKey().getGroup());

		try {
			List<? extends Trigger> triggers = scheduler.getTriggersOfJob(job.getKey());

			sb.append(", ").append(triggers.size()).append(" triggers=[");

			int maxTriggerLogs = 24;
			for (int triggerIndex = 0; triggerIndex < triggers.size() && triggerIndex < maxTriggerLogs; triggerIndex++) {
				Trigger trigger = triggers.get(triggerIndex);
				sb.append(trigger.getStartTime());
				if (triggerIndex < triggers.size() - 1 && triggerIndex < maxTriggerLogs - 1) {
					sb.append(", ");
				}
			}

			if (triggers.size() >= maxTriggerLogs) {
				sb.append(" and ").append(triggers.size() - maxTriggerLogs).append(" more ...");
			}

			if (triggers.size() == 0) {
				sb.append("there are no triggers - probably the event lies in the past");
			}
		}
		catch (SchedulerException e) {
		}

		/*
		 * 	sb.append("], content=").append(event.getPlainTextContent());
		 */

		return sb.toString();
	}


	/**
	 * Holds the parsed content of a GCal event
	 *  
	 * @author Thomas.Eichstaedt-Engelen
	 */
	class CalendarEventContent {
		String startCommands = "";
		String endCommands = "";
		String modifiedByEvent = "";
	}


	@Override
	public void updated(Dictionary<String, ?> config) throws ConfigurationException {
		if (config != null) {

			String usernameString = (String) config.get("username");
			username = usernameString;
			if (StringUtils.isBlank(username)) {
				throw new ConfigurationException("caldav:username", "username must not be blank - please configure an aproppriate username in openhab.cfg");
			}
			logger.trace("username: {}", username);

			String passwordString = (String) config.get("password");
			password = passwordString;
			if (StringUtils.isBlank(password)) {
				throw new ConfigurationException("caldav:password", "password must not be blank - please configure an aproppriate password in openhab.cfg");
			}
			logger.trace("password: {}", password);


			String hostString = (String) config.get("host");
			host = hostString;
			if (StringUtils.isBlank(host)) {
				throw new ConfigurationException("caldav:host", "host must not be blank - please configure an aproppriate host in openhab.cfg");
			}
			logger.trace("host: {}", host);

			String tlsString = (String) config.get("tls");
			if (StringUtils.isNotBlank(tlsString)) {
				try {
					tls = Boolean.parseBoolean(tlsString);
				}
				catch (IllegalArgumentException iae) {
					logger.warn("couldn't parse caldav:tls '{}' to a boolean");
				}
			} else {
				tls = true;
			}
			logger.trace("tls: {}", tls);


			String strictTlsString = (String) config.get("strict-tls");
			if (StringUtils.isNotBlank(strictTlsString)) {
				try {
					strictTls = Boolean.parseBoolean(strictTlsString);
				}
				catch (IllegalArgumentException iae) {
					logger.warn("couldn't parse caldav:strict-tls '{}' to a boolean");
				}
			} else {
				strictTls = true;
			}
			logger.trace("strictTls: {}", strictTls);
			if(!tls) {
				logger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				logger.warn("!!  You have disabled tls/ssl for CalDav-EventDownloader. Calendar data is exchanged unencrypted. !!");
				logger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			}


			if(!strictTls && tls) {
				logger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				logger.warn("!!  You have disabled strict certificate checking by setting strict-tls to false.    !!");
				logger.warn("!!  Actually all checking for certificates in CalDav-EventDownloader is disabled now !!");
				logger.warn("!!  - which means that there is no real security - as you accept any certificate,    !!");
				logger.warn("!!  even those which might be injected for Man-In The Middle-Attacks - try to        !!");
				logger.warn("!!  Register your certificate to your java certificate store and set strict-tls to   !!");
				logger.warn("!!  true. Disable the tls checking is just meant for debugging purposes.             !!");
				logger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			}

			String portString = (String) config.get("port");
			if (StringUtils.isNotBlank(portString)) {
				try {
					port = Integer.valueOf(portString);
				}
				catch (IllegalArgumentException iae) {
					logger.warn("couldn't parse caldav:port '{}' to an integer");
				}
			} else {
				if(tls) {
					port = 443;
				} else {
					port = 80;
				}
			}
			logger.trace("port: {}", port);




			String urlString = (String) config.get("url");
			url = urlString;
			if (StringUtils.isBlank(url)) {
				throw new ConfigurationException("caldav:url", "url must not be blank - please configure an aproppriate url in openhab.cfg");
			}
			logger.trace("url: {}", url);

			//			filter = (String) config.get("filter");

			String refreshString = (String) config.get("refresh");
			if (StringUtils.isNotBlank(refreshString)) {
				refreshInterval = Integer.parseInt(refreshString);
				refreshInterval *= 1000;
			}
			logger.trace("refreshInterval: {}ms", refreshInterval);

			setProperlyConfigured(true);
			logger.debug("CalDav event downloader successfuly configured");
		}
	}


}
