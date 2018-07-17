// Things left to do:
//
//   * Email error reports to someone who cares
//
//   * clean up logging
//
//   * Pull in rosters/schools/real user information from the right NYU_* tables
//
//   * Pull out config stuff into sakai.properties if useful (hot reload)
//
//   * Audit FIXMEs
//


package edu.nyu.classes.servlet;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;

import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.attendance.logic.AttendanceLogic;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.sakaiproject.db.cover.SqlService;
import java.util.stream.Collectors;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

import java.util.Collections;
import org.sakaiproject.attendance.model.AttendanceSite;
import org.sakaiproject.attendance.model.AttendanceRecord;
import org.sakaiproject.attendance.model.Status;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;




public class AttendanceGoogleReportExport {

    private static final String APPLICATION_NAME = "AttendanceGoogleReportExport";
    private static final String BACKUP_SHEET_NAME = "_backup_";
    private String spreadsheetId;
    private GoogleClient client;
    private Sheets service;

    public AttendanceGoogleReportExport() {
        String oauthPropertiesFile = HotReloadConfigurationService.getString("attendance-report.oauth-properties", "attendance_report_oauth_properties_not_set");
        oauthPropertiesFile = ServerConfigurationService.getSakaiHomePath() + "/" + oauthPropertiesFile;

        try {
            Properties oauthProperties = new Properties();
            try (FileInputStream fis = new FileInputStream(oauthPropertiesFile)) {
                oauthProperties.load(fis);
            }

            oauthProperties.setProperty("credentials_path", new File(new File(oauthPropertiesFile).getParentFile(),
                "oauth_credentials").getPath());

            this.client = new GoogleClient(oauthProperties);
            this.service = client.getSheets(APPLICATION_NAME);

            // FIXME from config
            // this.spreadsheetId = "1D4XcY7fQGfWu3ep_EDKOR-xIAoXPUp3sZyGfLp9ANNs";
            // this.spreadsheetId = "1BhsfNJl-3gfXyXMGqgoncMKvofQxFxICAHcdeO2iTDs";
            this.spreadsheetId = "1RVVvJIYPgazujEty_3MkQlX9oip66ItgY9ieF236py4";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    abstract static class ValueObject {
        public abstract Object[] interestingFields();

        @Override
        public int hashCode() { return Arrays.hashCode(interestingFields()); }

        @Override
        public boolean equals(Object other) {
            if (this == other) { return true; }
            if (this.getClass() != other.getClass()) { return false; }
            return Arrays.equals(this.interestingFields(), ((ValueObject) other).interestingFields());
        }

        @Override
        public String toString() {
            return this.interestingFields().toString();
        }
    }

    static class SiteUser extends ValueObject {
        public String netid;
        public String siteid;
        public String firstName;
        public String lastName;
        public String term;
        public String siteTitle;
        public String roster;

        public SiteUser(String netid, String siteid, String firstName, String lastName, String term, String siteTitle, String roster) {
            this.netid = Objects.requireNonNull(netid);
            this.siteid = Objects.requireNonNull(siteid);
            this.firstName = Objects.requireNonNull(firstName);
            this.lastName = Objects.requireNonNull(lastName);
            this.term = Objects.requireNonNull(term);
            this.siteTitle = Objects.requireNonNull(siteTitle);
            this.roster = Objects.requireNonNull(roster);
        }

        public SiteUser(String netid, String siteid) {
            this.netid = Objects.requireNonNull(netid);
            this.siteid = Objects.requireNonNull(siteid);
        }

        @Override
        public Object[] interestingFields() {
            return new Object[] { netid, siteid };
        }
    }

    static class AttendanceEvent extends ValueObject {
        public String name;

        public AttendanceEvent(String name) {
            this.name = Objects.requireNonNull(name);
        }

        @Override
        public Object[] interestingFields() {
            return new Object[] { name };
        }
    }

    static class UserAtEvent extends ValueObject {
        public SiteUser user;
        public AttendanceEvent event;

        public UserAtEvent(SiteUser user, AttendanceEvent event) {
            this.user = Objects.requireNonNull(user);
            this.event = Objects.requireNonNull(event);
        }

        @Override
        public Object[] interestingFields() {
            return new Object[] { user, event };
        }
    }

    static Map<String, String> statusMapping = null;
    static {
        statusMapping = new HashMap<>();
        statusMapping.put("PRESENT", "P");
        statusMapping.put("UNEXCUSED_ABSENCE", "A");
        statusMapping.put("EXCUSED_ABSENCE", "E");
        statusMapping.put("LATE", "L");
        statusMapping.put("LEFT_EARLY", "LE");
    }

    static class AttendanceOverride extends ValueObject {
        public UserAtEvent userAtEvent;
        public String override;
        public String oldStatus;
        public String rawText;

        public AttendanceOverride(UserAtEvent userAtEvent, String override, String oldStatus) {
            this.userAtEvent = Objects.requireNonNull(userAtEvent);
            this.rawText = Objects.requireNonNull(override);
            this.oldStatus = Objects.requireNonNull(oldStatus);

            for (Map.Entry<String, String> entry : statusMapping.entrySet()) {
                if (override.equals(entry.getValue())) {
                    this.override = entry.getKey();
                }
                if (oldStatus.equals(entry.getValue())) {
                    this.oldStatus = entry.getKey();
                }

            }

            if (this.override == null) {
                this.override = "";
            }
        }

        public boolean isValid() {
            return !"".equals(this.override);
        }

        @Override
        public Object[] interestingFields() {
            return new Object[] { userAtEvent, override, oldStatus };
        }
    }


    static class DataTable {
        public Set<AttendanceEvent> events;
        public List<SiteUser> users;
        public Map<UserAtEvent, String> statusTable;

        public DataTable(List<SiteUser> users, Set<AttendanceEvent> events, Map<UserAtEvent, String> statusTable) {
            this.users = users;
            this.events = events;
            this.statusTable = statusTable;
        }
    }

    private String mapStatus(String status) {
        return statusMapping.get(status);
    }

    private DataTable loadAllData() throws Exception {
        // Get a list of all students from the sites of interest
        // Get a list of the attendance events for all sites, joined to any attendance records

        Connection conn = SqlService.borrowConnection();
        try {
            // Get out list of users in sites of interest
            List<SiteUser> users = new ArrayList<>();
            Set<String> siteIds = new HashSet<>();

            // FIXME pull out London locations into config
            try (PreparedStatement ps = conn.prepareStatement("SELECT umap.eid, usr.fname, usr.lname, sess.descr as term, site.title, rlm.provider_id, site.site_id" +
                                                              " FROM sakai_realm_rl_gr srg" +
                                                              " INNER JOIN sakai_realm rlm ON rlm.realm_key = srg.realm_key" +
                                                              " INNER JOIN nyu_t_course_catalog cc ON REPLACE(cc.stem_name, ':', '_') = rlm.provider_id AND cc.location in ('1L','2L','GLOBAL-0L', 'LO')" +
                                                              " INNER JOIN nyu_t_acad_session sess ON cc.strm = sess.strm AND cc.acad_career = sess.acad_career AND sess.current_flag = 'Y'" +
                                                              " INNER JOIN sakai_site site ON site.site_id = REPLACE(rlm.REALM_ID, '/site/', '')" +
                                                              " INNER JOIN attendance_site_t att ON att.site_id = site.site_id" +
                                                              " INNER JOIN sakai_user_id_map umap ON umap.user_id = srg.user_id" +
                                                              " INNER JOIN nyu_t_users usr ON usr.netid = umap.eid" +
                                                              " WHERE srg.role_key IN (SELECT role_key FROM sakai_realm_role WHERE role_name = 'Student')");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(new SiteUser(rs.getString("eid"), rs.getString("site_id"), rs.getString("fname"), rs.getString("lname"), rs.getString("term"), rs.getString("title"), rs.getString("provider_id")));
                    siteIds.add(rs.getString("site_id"));
                }
            }

            // FIXME this will blow up if siteIds.length > 1000 .. do we care?
            String siteIdQueryString = siteIds.stream().map(n -> String.format("'%s'", n)).collect(Collectors.joining(","));

            // Get our mapping of events to the sites that have them
            Map<AttendanceEvent, Set<String>> sitesWithEvent = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("select e.name, s.site_id" +
                                                              " from attendance_event_t e" +
                                                              " inner join attendance_site_t s on s.a_site_id = e.a_site_id" +
                                                              " where s.site_id in (" + siteIdQueryString + ")");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AttendanceEvent event = new AttendanceEvent(rs.getString("name"));

                    if (!sitesWithEvent.containsKey(event)) {
                        sitesWithEvent.put(event, new HashSet<>());
                    }

                    sitesWithEvent.get(event).add(rs.getString("site_id"));
                }

            }

            Set<AttendanceEvent> events = sitesWithEvent.keySet();
            Map<UserAtEvent, String> statusTable = new HashMap<>();

            // If a user is in a site that doesn't have a particular event, that's a "-"
            for (SiteUser user : users) {
                for (AttendanceEvent event : events) {
                    if (!sitesWithEvent.get(event).contains(user.siteid)) {
                        statusTable.put(new UserAtEvent(user, event), "-");
                    }
                }
            }

            // Get all users at all events
            try (PreparedStatement ps = conn.prepareStatement("select s.site_id, e.name, m.eid, r.status" +
                                                              " from attendance_event_t e" +
                                                              " inner join attendance_record_t r on e.a_event_id = r.a_event_id" +
                                                              " inner join attendance_site_t s on e.a_site_id = s.a_site_id" +
                                                              " inner join sakai_user_id_map m on m.user_id = r.user_id" +
                                                              " where s.site_id in (" + siteIdQueryString + ")");
                 ResultSet rs = ps.executeQuery()) {
                // Fill out the values we know
                while (rs.next()) {
                    SiteUser user = new SiteUser(rs.getString("eid"), rs.getString("site_id"));
                    AttendanceEvent event = new AttendanceEvent(rs.getString("name"));

                    String status = mapStatus(rs.getString("status"));

                    if (status != null) {
                        statusTable.put(new UserAtEvent(user, event), status);
                    }
                }
            }

            // And fill out any that were missing as UKNOWN
            for (SiteUser user : users) {
                for (AttendanceEvent event : events) {
                    UserAtEvent key = new UserAtEvent(user, event);

                    if (!statusTable.containsKey(key)) {
                        statusTable.put(key, "UNKNOWN");
                    }
                }
            }

            return new DataTable(users, events, statusTable);

        } finally {
            SqlService.returnConnection(conn);
        }
    }

    public void export() {
        try {
            Sheet sheet = getTargetSheet();

            if (backupExists()) {
                // FIXME
                throw new RuntimeException("Backup sheet exists! Stop everything!");
            }

            ProtectedRange range = protectSheet(sheet);

            try {
                storeOverrides(pullOverrides(sheet));

                backupSheet(sheet);

                clearSheet(sheet);

                syncValuesToSheet(sheet);

                protectNonEditableColumns(sheet, range);

                deleteSheet(BACKUP_SHEET_NAME);
            } finally {
                unprotectRange(sheet, range);
            }

        } catch (Exception e) {
            System.out.println("ERROR in AttendanceGoogleReportExport.export");
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean backupExists() throws IOException {
        Sheets.Spreadsheets.Get getSpreadsheetRequest = service.spreadsheets().get(spreadsheetId);
        Spreadsheet spreadsheet = getSpreadsheetRequest.execute();

        for (Sheet sheet : spreadsheet.getSheets()) {
            if (BACKUP_SHEET_NAME.equals(sheet.getProperties().getTitle())) {
                return true;
            }
        }

        return false;
    }

    private void deleteSheet(String sheetName) throws IOException {
        System.out.println("Delete sheet: " + sheetName);

        Sheets.Spreadsheets.Get getSpreadsheetRequest = service.spreadsheets().get(spreadsheetId);
        Spreadsheet spreadsheet = getSpreadsheetRequest.execute();
        List<Request> requests = new ArrayList<>();

        for (Sheet sheet : spreadsheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(sheetName)) {
                DeleteSheetRequest deleteSheetRequest = new DeleteSheetRequest();
                deleteSheetRequest.setSheetId(sheet.getProperties().getSheetId());

                Request request = new Request();
                request.setDeleteSheet(deleteSheetRequest);
                requests.add(request);
            }
        }

        if (requests.isEmpty()) {
            return;
        }

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest =
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);

        BatchUpdateSpreadsheetResponse batchUpdateSpreadsheetResponse = batchUpdateRequest.execute();
        System.out.println(batchUpdateSpreadsheetResponse);
    }

    private void backupSheet(Sheet sheet) throws IOException {
        System.out.println("Backup sheet");

        DuplicateSheetRequest duplicateSheetRequest = new DuplicateSheetRequest();
        duplicateSheetRequest.setSourceSheetId(sheet.getProperties().getSheetId());
        duplicateSheetRequest.setInsertSheetIndex(1);
        duplicateSheetRequest.setNewSheetName(BACKUP_SHEET_NAME);

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        List<Request> requests = new ArrayList<>();
        Request request = new Request();
        request.setDuplicateSheet(duplicateSheetRequest);
        requests.add(request);
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest =
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);

        BatchUpdateSpreadsheetResponse batchUpdateSpreadsheetResponse = batchUpdateRequest.execute();
        Response response = batchUpdateSpreadsheetResponse.getReplies().get(0);

        DuplicateSheetResponse duplicateSheetResponse = response.getDuplicateSheet();
        protectSheet(duplicateSheetResponse.getProperties().getSheetId());
    }

    private void storeOverrides(List<AttendanceOverride> overrides) {
        // netid, siteid, event name...
        AttendanceLogic attendance = (AttendanceLogic) ComponentManager.get("org.sakaiproject.attendance.logic.AttendanceLogic");

        for (AttendanceOverride override : overrides) {
            if (!override.isValid()) {
                // "INVALID OVERRIDE", override.rawText
                System.err.println("\n*** DEBUG " + System.currentTimeMillis() + "[AttendanceGoogleReportExport.java:315 f46b87]: " + "\n    'INVALID OVERRIDE' => " + ("INVALID OVERRIDE") + "\n    override.rawText => " + (override.rawText) + "\n");
                continue;
            }

            boolean updated = false;

            String netid = override.userAtEvent.user.netid;
            try {
                User user = UserDirectoryService.getUserByEid(netid);

                AttendanceSite attendanceSite = attendance.getAttendanceSite(override.userAtEvent.user.siteid);

                List<AttendanceRecord> records =
                    attendance
                    .getAttendanceRecordsForUsers(Collections.singletonList(user.getId()),
                                                  attendanceSite)
                    .get(user.getId());

                if (records == null) {
                    records = Collections.emptyList();
                }

                for (AttendanceRecord record : records) {
                    if (override.userAtEvent.event.name.equals(record.getAttendanceEvent().getName())) {
                        Status oldStatus = record.getStatus();

                        if (oldStatus.toString().equals(override.oldStatus)) {
                            record.setStatus(Status.valueOf(override.override));
                            attendance.updateAttendanceRecord(attendanceSite, record, oldStatus);
                        } else {
                            // FIXME
                            System.err.println("WARNING: database status " + oldStatus + " doesn't match incoming " + override.oldStatus);
                        }
                        updated = true;
                        break;
                    }
                }

                if (!updated) {
                    // "FAILED TO FIND MATCH", override.userAtEvent.event.name, override.userAtEvent.user.netid
                    System.err.println("\n*** DEBUG " + System.currentTimeMillis() + "[AttendanceGoogleReportExport.java:349 f69489]: " + "\n    'FAILED TO FIND MATCH' => " + ("FAILED TO FIND MATCH") + "\n    override.userAtEvent.event.name => " + (override.userAtEvent.event.name) + "\n    override.userAtEvent.user.netid => " + (override.userAtEvent.user.netid) + "\n");
                }
            } catch (UserNotDefinedException e) {
                // FIXME: failed to match user
            }

        }
    }

    private List<AttendanceOverride> pullOverrides(Sheet sheet) throws IOException {
        Sheets.Spreadsheets.Values.Get request = service.spreadsheets().values().get(spreadsheetId, sheet.getProperties().getTitle());
        ValueRange values = request.execute();

        List<Object> headers = values.getValues().get(0);

        String[] overrideEvents = new String[headers.size()];

        for (int i = 0; i < headers.size(); i++) {
            if (((String) headers.get(i)).endsWith("\nOVERRIDE")) {
                String eventName = (String) headers.get(i - 1);
                overrideEvents[i] = eventName;
            }
        }

        List<AttendanceOverride> result = new ArrayList<>();

        for (int i = 1; i < values.getValues().size(); i++) {
            List<Object> row = values.getValues().get(i);
            String netId = (String)row.get(0);
            String siteUrl = (String)row.get(6);

            for (int override = 0; override < overrideEvents.length && override < row.size(); override++) {
                if (overrideEvents[override] == null) {
                    // This isn't an override column.  Ignore.
                    continue;
                }

                if ("-".equals(row.get(override - 1))) {
                    // You can't override an event the student isn't in.
                    continue;
                }

                if (row.get(override) == null || "".equals(row.get(override))) {
                    // No override specified for this student.
                    continue;
                }

                SiteUser user = new SiteUser(netId, siteUrl.replaceAll(".*/", ""));
                AttendanceEvent event = new AttendanceEvent(overrideEvents[override]);
                UserAtEvent userAtEvent = new UserAtEvent(user, event);

                result.add(new AttendanceOverride(userAtEvent, (String)row.get(override), (String)row.get(override - 1)));
            }
        }

        return result;
    }

    private ProtectedRange protectSheet(Integer sheetId) throws IOException {
        System.out.println("Protect sheet: " + sheetId);
        AddProtectedRangeRequest addProtectedRangeRequest = new AddProtectedRangeRequest();
        ProtectedRange protectedRange = new ProtectedRange();
        GridRange gridRange = new GridRange();
        gridRange.setSheetId(sheetId);
        protectedRange.setRange(gridRange);
        protectedRange.setEditors(new Editors());
        protectedRange.setRequestingUserCanEdit(true);
        addProtectedRangeRequest.setProtectedRange(protectedRange);

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        List<Request> requests = new ArrayList<>();
        Request wrapperRequest = new Request();
        wrapperRequest.setAddProtectedRange(addProtectedRangeRequest);
        requests.add(wrapperRequest);
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest =
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);

        BatchUpdateSpreadsheetResponse batchUpdateSpreadsheetResponse = batchUpdateRequest.execute();
        System.out.println(batchUpdateSpreadsheetResponse);
        AddProtectedRangeResponse addProtectedRangeResponse = batchUpdateSpreadsheetResponse.getReplies().get(0).getAddProtectedRange();

        return addProtectedRangeResponse.getProtectedRange();
    }

    private ProtectedRange protectSheet(Sheet sheet) throws IOException {
        return protectSheet(sheet.getProperties().getSheetId());
    }

    private void unprotectRange(Sheet sheet, ProtectedRange range) throws IOException {
        List<Request> requests = new ArrayList<>();

        DeleteProtectedRangeRequest deleteProtectedRangeRequest = new DeleteProtectedRangeRequest();
        deleteProtectedRangeRequest.setProtectedRangeId(range.getProtectedRangeId());
        Request request = new Request();
        request.setDeleteProtectedRange(deleteProtectedRangeRequest);
        requests.add(request);

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest = service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);
        BatchUpdateSpreadsheetResponse batchUpdateSpreadsheetResponse = batchUpdateRequest.execute();

        System.out.println(batchUpdateSpreadsheetResponse);
    }

    private void clearSheet(Sheet sheet) throws IOException {
        System.out.println("Clear the sheet");
        Sheets.Spreadsheets.Values.Clear clearRequest =
            service.spreadsheets().values().clear(spreadsheetId, sheet.getProperties().getTitle(), new ClearValuesRequest());
        ClearValuesResponse clearValuesResponse = clearRequest.execute();
        System.out.println(clearValuesResponse);
    }

    private void syncValuesToSheet(Sheet sheet) throws Exception {
        System.out.println("Give it some values");
        ValueRange valueRange = new ValueRange();
        List<List<Object>> rows = new ArrayList<>();

        DataTable table = loadAllData();

        // Add a row for our header
        List<Object> header = new ArrayList<>();
        header.add("NetID");
        header.add("Last Name");
        header.add("First Name");
        header.add("Term");
        header.add("Course Title");
        header.add("Roster ID");
        header.add("Site URL");

        for (AttendanceEvent event : table.events) {
            header.add(event.name);
            header.add(event.name + "\nOVERRIDE");
        }
        rows.add(header);

        // Now our student data
        for (SiteUser user : table.users) {
            List<Object> row = new ArrayList<>();
            row.add(user.netid);
            row.add(user.firstName);
            row.add(user.lastName);
            row.add(user.term);
            row.add(user.siteTitle);
            row.add(user.roster);
            row.add("https://newclasses.nyu.edu/portal/site/" + user.siteid);

            for (AttendanceEvent event : table.events) {
                row.add(table.statusTable.get(new UserAtEvent(user, event)));
                row.add("");
            }

            rows.add(row);
        }

        valueRange.setValues(rows);

        Sheets.Spreadsheets.Values.Update updateRequest =
            service.spreadsheets().values().update(spreadsheetId, sheet.getProperties().getTitle() + "!A1:ZZ", valueRange);
        updateRequest.setValueInputOption("RAW");
        UpdateValuesResponse updateValuesResponse = updateRequest.execute();
        System.out.println(updateValuesResponse);
    }

    private Sheet getTargetSheet() throws IOException {
        System.out.println("Get the sheet");
        List<String> ranges = new ArrayList<>();
        Sheets.Spreadsheets.Get request = service.spreadsheets().get(spreadsheetId);
        request.setRanges(ranges);
        request.setIncludeGridData(false);
        Spreadsheet spreadsheet = request.execute();

        return spreadsheet.getSheets().get(0);
    }

    private void protectNonEditableColumns(Sheet targetSheet, ProtectedRange sheetProtectedRange) throws IOException {
        System.out.println("Protect non editable columns");

        // All requests to apply to the spreadsheet
        List<Request> requests = new ArrayList<>();

        // Build requests to drop all existing protected ranges (except sheetProtectingRange)
        System.out.println("- get existing protected ranges for deletion");
        Sheets.Spreadsheets.Get getSpreadsheetRequest = service.spreadsheets().get(spreadsheetId);
        Spreadsheet spreadsheet = getSpreadsheetRequest.execute();
        for (Sheet sheet : spreadsheet.getSheets()) {
            if (targetSheet.getProperties().getSheetId().equals(sheet.getProperties().getSheetId())) {
                for (ProtectedRange protectedRange : sheet.getProtectedRanges()) {
                    if (sheetProtectedRange.getProtectedRangeId().equals(protectedRange.getProtectedRangeId())) {
                        continue;
                    }

                    DeleteProtectedRangeRequest deleteProtectedRangeRequest = new DeleteProtectedRangeRequest();
                    deleteProtectedRangeRequest.setProtectedRangeId(protectedRange.getProtectedRangeId());
                    Request request = new Request();
                    request.setDeleteProtectedRange(deleteProtectedRangeRequest);
                    requests.add(request);
                }
            }
        }

        // Build requests to protected each non-OVERRIDE column
        System.out.println("- build new protected ranges from headers");
        Sheets.Spreadsheets.Values.Get spreadsheetGetRequest = service.spreadsheets().values().get(spreadsheetId, targetSheet.getProperties().getTitle() + "!A1:ZZ1");
        ValueRange values = spreadsheetGetRequest.execute();

        List<Object> headers = values.getValues().get(0);
        for (int i=0; i < headers.size(); i++) {
            String header = (String) headers.get(i);
            if (header.endsWith("\nOVERRIDE")) {
                continue;
            }

            ProtectedRange protectedRange = new ProtectedRange();
            GridRange gridRange = new GridRange();
            gridRange.setSheetId(targetSheet.getProperties().getSheetId());
            gridRange.setStartColumnIndex(i);
            gridRange.setEndColumnIndex(i+1);
            protectedRange.setRange(gridRange);
            protectedRange.setEditors(new Editors());
            protectedRange.setRequestingUserCanEdit(true);

            AddProtectedRangeRequest addProtectedRangeRequest = new AddProtectedRangeRequest();
            addProtectedRangeRequest.setProtectedRange(protectedRange);

            Request request = new Request();
            request.setAddProtectedRange(addProtectedRangeRequest);
            requests.add(request);
        }

        // protect the header row
        ProtectedRange protectedRange = new ProtectedRange();
        GridRange gridRange = new GridRange();
        gridRange.setSheetId(targetSheet.getProperties().getSheetId());
        gridRange.setStartRowIndex(0);
        gridRange.setEndRowIndex(1);
        protectedRange.setRange(gridRange);
        protectedRange.setEditors(new Editors());
        protectedRange.setRequestingUserCanEdit(true);
        AddProtectedRangeRequest addProtectedRangeRequest = new AddProtectedRangeRequest();
        addProtectedRangeRequest.setProtectedRange(protectedRange);
        Request request = new Request();
        request.setAddProtectedRange(addProtectedRangeRequest);
        requests.add(request);

        // Do the request!
        System.out.println("- do the batch request");
        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        batchUpdateSpreadsheetRequest.setRequests(requests);
        Sheets.Spreadsheets.BatchUpdate batchUpdateRequest =
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest);

        BatchUpdateSpreadsheetResponse batchUpdateSpreadsheetResponse = batchUpdateRequest.execute();

        System.out.println(batchUpdateSpreadsheetResponse);
    }
}
