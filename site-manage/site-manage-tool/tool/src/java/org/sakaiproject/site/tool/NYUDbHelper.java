package org.sakaiproject.site.tool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.component.cover.HotReloadConfigurationService;

/**
 * NYUDbHelper abstracts the DB calls to the additional NYU_ specific DB tables.
 * 
 * @author Steve Swinsburg (steve.swinsburg@gmail.com)
 * @author Mark Triggs (mark.triggs@nyu.edu)
 *
 */

public class NYUDbHelper {

	String DEFAULT_KEY = "DEFAULT";

	private SqlService sqlService;
	private static Log M_log = LogFactory.getLog(NYUDbHelper.class);
	
	public NYUDbHelper() {
		if(sqlService == null) {
			sqlService = (SqlService) org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.db.api.SqlService");
		}
	}
	
	protected String findSponsor(String sectionEid) {

		try {
			Connection db = sqlService.borrowConnection();

			try {
				PreparedStatement ps = db.prepareStatement("select sponsor_course " +
						"from NYU_T_CROSSLISTINGS " +
						"where nonsponsor_course = ?");
				ps.setString(1, sectionEid.replace("_", ":"));

				ResultSet rs = ps.executeQuery();
				try {
					if (rs.next()) {
						return rs.getString(1).replace(":", "_");
					}
				} finally {
					rs.close();
				}
			} finally {
				sqlService.returnConnection(db);
			}
		} catch (SQLException e) {
			M_log.warn(this + ".findSponsor: " + e);
		}
		return null;
	}
	
	/**
	 * Get the description value for the section
	 * @param sectionEid
	 * @return
	 */
	protected String getSiteDescription(String sectionEid) {
		return getPropertyFromCourseCatalog(sectionEid, "descrlong");	
	}
	
	/*
	 "Department", "School", "Location
	 c.acad_org as department
	 c.acad_group as school
	 c.campus as location
	 */
	
	/**
	 * Get the short description value for the site
	 * @param sectionEid
	 * @return
	 */
	protected String getSiteShortDescription(String sectionEid) {
		return getPropertyFromCourseCatalog(sectionEid, "descr");
	}
	
	/**
	 * Get the department value for the site
	 * @param sectionEid
	 * @return
	 */
	protected String getSiteDepartment(String sectionEid) {
		return getPropertyFromCourseCatalog(sectionEid, "acad_org");
	}
	
	/**
	 * Get the school value for the site
	 * @param sectionEid
	 * @return
	 */
	protected String getSiteSchool(String sectionEid) {
		return getPropertyFromCourseCatalog(sectionEid, "acad_group");
	}
	
	/**
	 * Get the location/campus for the site
	 * @param sectionEid
	 * @return
	 */
	protected String getSiteLocation(String sectionEid) {
		return getPropertyFromCourseCatalog(sectionEid, "location");
	}

	/**
	 * Get the instruction mode for the site (e.g. online, in person, hybrid)
	 * @param sectionEid
	 * @return
	 */
	protected String getSiteInstructionMode(String sectionEid) {
		return getPropertyFromCourseCatalog(sectionEid, "instruction_mode");
	}

	/**
	 * Helper to do the DB calls for us onto nyu_t_course_catalog table, given a sectionEid and a column name. 
	 * The sectionEid is the stem_name with separators replaced.
	 * @param sectionEid
	 * @param columnName
	 * @return
	 */
	private String getPropertyFromCourseCatalog(String sectionEid, String columnName) {
		try {
			Connection db = sqlService.borrowConnection();

			try {
				PreparedStatement ps = db.prepareStatement("select " + columnName + " from NYU_T_COURSE_CATALOG where stem_name = ?");
				ps.setString(1, sectionEid.replace("_", ":"));

				ResultSet rs = ps.executeQuery();
				try {
					if (rs.next()) {
						return rs.getString(1);
					}
				} finally {
					rs.close();
				}
			} finally {
				sqlService.returnConnection(db);
			}
		} catch (SQLException e) {
			M_log.warn(this + ".getPropertyFromCourseCatalog: " + e);
		}
		return null;	
	}
	

	protected String schoolCodeLookup(String schoolCode) {
		try {
			Connection db = sqlService.borrowConnection();

			try {
				PreparedStatement ps = db.prepareStatement("SELECT template_site_id FROM nyu_t_site_templates WHERE school_code = ?");
				ps.setString(1, schoolCode);

				ResultSet rs = ps.executeQuery();
				try {
					if (rs.next()) {
						return rs.getString(1);
					}
				} finally {
					rs.close();
				}
			} finally {
				sqlService.returnConnection(db);
			}
		} catch (SQLException e) {
			M_log.warn(this + ".getSiteTemplateForSchoolCode: " + e);
		}

		return null;
	}


	protected String getSiteTemplateForSchoolCode(String schoolCode, String termCode) {

		if (StringUtils.isBlank(schoolCode)) {
			return schoolCodeLookup(DEFAULT_KEY);
		}

		String result = null;

		result = schoolCodeLookup(schoolCode);

		if (result == null) {
		    result = schoolCodeLookup(DEFAULT_KEY);
		}

		M_log.info("Selected template for school " + schoolCode + " and term " + termCode + ": " + result);

		return result;
	}


	protected boolean isCurrentUserDental() {
		Connection db = null;
		try {
			db = sqlService.borrowConnection();

			String netid = UserDirectoryService.getCurrentUser().getEid();
			String dentalSchoolCodes = HotReloadConfigurationService.getString("nyu.dental-school-codes", "'DN', 'UD', 'CD'");

			String[] queries = new String[] {
				String.format("SELECT count(1) " +
					      " from NYU_T_INSTRUCTORS i " +
					      " inner join NYU_T_COURSE_CATALOG cc on cc.stem_name = i.stem_name AND cc.acad_group in (%s)" +
					      " where i.instr_role IN ('12', 'PI', '11') AND i.netid = ?",
					      dentalSchoolCodes),

				String.format("SELECT count(1) " +
					      " from NYU_T_COURSE_ADMINS a " +
					      " inner join NYU_T_COURSE_CATALOG cc on cc.stem_name = a.stem_name AND cc.acad_group in (%s)" +
					      " where a.netid = ?",
					      dentalSchoolCodes),
			};


			PreparedStatement ps;
			for (String query : queries) {
				ps = db.prepareStatement(query);
				ps.setString(1, netid);

				ResultSet rs = null;
				try {
					rs = ps.executeQuery();
					if (rs.next()) {
						if (rs.getInt(1) > 0) {
							return true;
						}
					}
				} finally {
					if (rs != null) { rs.close(); }
					if (ps != null) { ps.close(); }
				}
			}
		} catch (SQLException e) {
			M_log.warn(this + ".isCurrentUserDental: " + e);
		} finally {
			sqlService.returnConnection(db);
		}

		return false;
	}

}
