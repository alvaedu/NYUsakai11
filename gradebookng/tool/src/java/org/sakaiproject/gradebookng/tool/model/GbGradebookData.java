package org.sakaiproject.gradebookng.tool.model;

import lombok.Data;
import lombok.Setter;
import lombok.Value;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.wicket.model.StringResourceModel;
import org.sakaiproject.gradebookng.business.GbCategoryType;
import org.sakaiproject.gradebookng.business.GbRole;
import org.sakaiproject.gradebookng.business.model.GbCourseGrade;
import org.sakaiproject.gradebookng.business.model.GbStudentNameSortOrder;
import org.sakaiproject.gradebookng.tool.pages.GradebookPage;
import org.sakaiproject.gradebookng.business.model.GbStudentGradeInfo;

import org.apache.wicket.Component;
import org.sakaiproject.gradebookng.business.util.FormatHelper;
import org.sakaiproject.gradebookng.business.model.GbGradeInfo;
import java.io.UnsupportedEncodingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.sakaiproject.service.gradebook.shared.Assignment;
import org.sakaiproject.service.gradebook.shared.CategoryDefinition;
import org.sakaiproject.service.gradebook.shared.CourseGrade;
import org.sakaiproject.service.gradebook.shared.GradebookInformation;
import org.sakaiproject.service.gradebook.shared.GradingType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GbGradebookData {

    private int NULL_SENTINEL = 127;

    @Data
    private class StudentDefinition {
        private String eid;
        private String userId;
        private String firstName;
        private String lastName;

        private String hasComments;
        private String hasConcurrentEdit;
        private String readonly;
    }

    private interface ColumnDefinition {
        public String getType();
        public Score getValueFor(GbStudentGradeInfo studentGradeInfo, boolean isInstructor);
    }

    @Value
    private class AssignmentDefinition implements ColumnDefinition {
        private Long assignmentId;
        private String title;
        private String abbrevTitle;
        private String points;
        private String dueDate;

        private boolean isReleased;
        private boolean isIncludedInCourseGrade;
        private boolean isExtraCredit;
        private boolean isExternallyMaintained;
        private String externalId;
        private String externalAppName;
        private String externalAppIconCSS;

        private String categoryId;
        private String categoryName;
        private String categoryColor;
        private String categoryWeight;
        private boolean isCategoryExtraCredit;

        private boolean hidden;

        @Override
        public String getType() {
            return "assignment";
        }

        @Override
        public Score getValueFor(GbStudentGradeInfo studentGradeInfo, boolean isInstructor) {
            Map<Long, GbGradeInfo> studentGrades = studentGradeInfo.getGrades();

            GbGradeInfo gradeInfo = studentGrades.get(assignmentId);

            if (gradeInfo == null) {
                return new ReadOnlyScore(null);
            } else {
                String grade = gradeInfo.getGrade();

                if (isInstructor || gradeInfo.isGradeable()) {
                    return new EditableScore(grade);
                } else {
                    return new ReadOnlyScore(grade);
                }
            }
        }
    }

    @Value
    private class CategoryAverageDefinition implements ColumnDefinition {
        private Long categoryId;
        private String categoryName;
        private String title;
        private String weight;
        private boolean isExtraCredit; 
        private String color;
        private boolean hidden;

        @Override
        public String getType() {
            return "category";
        }

        @Override
        public Score getValueFor(GbStudentGradeInfo studentGradeInfo, boolean isInstructor) {
            Map<Long, Double> categoryAverages = studentGradeInfo.getCategoryAverages();

            Double average = categoryAverages.get(categoryId);

            if (average == null) {
                return new ReadOnlyScore(null);
            } else {
                return new ReadOnlyScore(FormatHelper.formatDoubleToDecimal(average));
            }
        }
    }

    @Value
    private class DataSet {
        private List<StudentDefinition> students;
        private List<ColumnDefinition> columns;
        private List<String[]> courseGrades;
        private String serializedGrades;
        private Map<String, Object> settings;

        private int rowCount;
        private int columnCount;

        public DataSet(List<StudentDefinition> students,
                       List<ColumnDefinition> columns,
                       List<String[]> courseGrades,
                       String serializedGrades,
                       Map<String, Object> settings) {
            this.students = students;
            this.columns = columns;
            this.courseGrades = courseGrades;
            this.serializedGrades = serializedGrades;
            this.settings = settings;

            this.rowCount = students.size();
            this.columnCount = columns.size();
        }
    }

    private List<StudentDefinition> students;
    private List<ColumnDefinition> columns;
    private List<GbStudentGradeInfo> studentGradeInfoList;
    private List<CategoryDefinition> categories;
    private GradebookInformation settings;
    private GradebookUiSettings uiSettings;
    private GbRole role;
    private Map<String, String> toolNameIconCSSMap;
    private String defaultIconCSS;
    private Map<String, Double> courseGradeMap;

    private Component parent;

    public GbGradebookData(List<GbStudentGradeInfo> studentGradeInfoList,
                           List<Assignment> assignments,
                           List<CategoryDefinition> categories,
                           GradebookInformation settings,
                           GradebookUiSettings uiSettings,
                           GbRole role,
                           Map<String, String> toolNameIconCSSMap,
                           String defaultIconCSS,
                           Map<String, Double> courseGradeMap,
                           Component parentComponent) {
        this.parent = parentComponent;
        this.categories = categories;
        this.settings = settings;
        this.uiSettings = uiSettings;
        this.role = role;

        this.courseGradeMap = courseGradeMap;

        this.studentGradeInfoList = studentGradeInfoList;

        this.toolNameIconCSSMap = toolNameIconCSSMap;
        this.defaultIconCSS = defaultIconCSS;

        this.columns = loadColumns(assignments);
        this.students = loadStudents(studentGradeInfoList);
    }

    public String toScript() {
        ObjectMapper mapper = new ObjectMapper();

        List<Score> grades = gradeList();

        // if we can't edit one of the items,
        // we need to serialize this into the data
        if (!isInstructor() && grades.stream().anyMatch(g -> !g.canEdit())) {
            int i = 0;
            for(StudentDefinition student : this.students) {
                String readonly = "";
                for (ColumnDefinition column : this.columns) {
                    Score score = grades.get(i);
                    readonly += score.canEdit() ? "0" : "1";
                    i = i + 1;
                }
                student.setReadonly(readonly);
            }
        }

        DataSet dataset = new DataSet(
            this.students, 
            this.columns,
            courseGrades(),
            serializeGrades(grades),
            serializeSettings());

        try {
            return mapper.writeValueAsString(dataset);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String serializeGrades(List<Score> gradeList) {
        if (gradeList.stream().anyMatch(score -> score.isLarge())) {
            return "json:" + serializeLargeGrades(gradeList);
        } else {
            return "packed:" + serializeSmallGrades(gradeList);
        }
    }

    private String serializeLargeGrades(List<Score> gradeList) {
        List<Double> scores = gradeList.stream().map(score -> score.isNull() ? -1 : score.getScore()).collect(Collectors.toList());

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(scores);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    ///
    // Pack our list of scores into as little space as possible.
    //
    // Most scores will be between 0 - 100, so we store small numbers in a
    // single byte.  Larger scores will be stored in two bytes, and scores with
    // fractional parts in three bytes.
    //
    // We go to all of this effort because grade tables can be very large.  A
    // site with 2,000 students and 100 gradeable items will have 200,000
    // scores.  Even if all of those scores were between 0 and 100, encoding
    // them as a JSON array would use around 3 bytes per score (two digits plus
    // a comma separator), for a total of 600KB.  We can generally at least
    // halve that number by byte packing the numbers ourselves and unpacking
    // them using JavaScript on the client.
    //
    // Why not just use AJAX and send them a chunk at a time?  The GradebookNG
    // design is built around having a single large table of all grades, and
    // firing AJAX requests on scroll events ends up being prohibitively slow.
    // Having all the data available up front helps keep the scroll performance
    // fast.
    //
    private String serializeSmallGrades(List<Score> gradeList) {
        StringBuilder sb = new StringBuilder();

        for (Score score : gradeList) {
            if (score == null || score.isNull()) {
                // No grade set.  Use a sentinel value.
                sb.appendCodePoint(NULL_SENTINEL);
                continue;
            }

            double grade = score.getScore();

            boolean hasFraction = ((int)grade != grade);

            if (grade < 127 && !hasFraction) {
                // single byte, no fraction
                //
                // input number like 0nnnnnnn serialized as 0nnnnnnn
                sb.appendCodePoint((int)grade & 0xFF);
            } else if (grade < 16384 && !hasFraction) {
                // two byte, no fraction
                //
                // input number like 00nnnnnn nnnnnnnn serialized as 10nnnnnn nnnnnnnn
                //
                // where leading '10' means 'two bytes, no fraction part'
                sb.appendCodePoint(((int)grade >> 8) | 0b10000000);
                sb.appendCodePoint(((int)grade & 0xFF));
            } else if (grade < 16384) {
                // three byte encoding, fraction
                //
                // input number like 00nnnnnn nnnnnnnn.25 serialized as 11nnnnnn nnnnnnnn 00011001
                //
                // where leading '11' means 'two bytes plus a fraction part',
                // and the fraction part is stored as an integer between 0-99,
                // where 50 represents 0.5, 25 represents .25, etc.

                sb.appendCodePoint(((int)grade >> 8) | 0b11000000);
                sb.appendCodePoint((int)grade & 0xFF);
                sb.appendCodePoint((int)Math.round((grade * 100) - ((int)grade * 100)));
            } else {
                throw new RuntimeException("Grade too large: " + grade);
            }
        }

        try {
            return Base64.getEncoder().encodeToString(sb.toString().getBytes("ISO-8859-1"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> serializeSettings() {
        Map<String, Object> result = new HashedMap();

        result.put("isCourseLetterGradeDisplayed", settings.isCourseLetterGradeDisplayed());
        result.put("isCourseAverageDisplayed", settings.isCourseAverageDisplayed());
        result.put("isCoursePointsDisplayed", settings.isCoursePointsDisplayed());
        result.put("isPointsGradeEntry", GradingType.valueOf(settings.getGradeType()).equals(GradingType.POINTS));
        result.put("isPercentageGradeEntry", GradingType.valueOf(settings.getGradeType()).equals(GradingType.PERCENTAGE));
        result.put("isCategoriesEnabled", GbCategoryType.valueOf(settings.getCategoryType()) != GbCategoryType.NO_CATEGORY);
        result.put("isCategoryTypeWeighted", GbCategoryType.valueOf(settings.getCategoryType()) == GbCategoryType.WEIGHTED_CATEGORY);
        result.put("isStudentOrderedByLastName", uiSettings.getNameSortOrder() == GbStudentNameSortOrder.LAST_NAME);
        result.put("isStudentOrderedByFirstName", uiSettings.getNameSortOrder() == GbStudentNameSortOrder.FIRST_NAME);
        result.put("isGroupedByCategory", uiSettings.isGroupedByCategory());
        result.put("isCourseGradeReleased", settings.isCourseGradeDisplayed());
        result.put("showPoints", uiSettings.getShowPoints());
        result.put("instructor", isInstructor());

        return result;
    };

    private List<String[]> courseGrades() {
        List<String[]> result = new ArrayList<String[]>();


        final Map<String, Double> gradeMap = settings.getSelectedGradingScaleBottomPercents();
        final List<String> ascendingGrades = new ArrayList<>(gradeMap.keySet());
        ascendingGrades.sort(new Comparator<String>() {
            @Override
            public int compare(final String a, final String b) {
                return new CompareToBuilder()
                    .append(gradeMap.get(a), gradeMap.get(b))
                    .toComparison();
            }
        });

        for (GbStudentGradeInfo studentGradeInfo : this.studentGradeInfoList) {
            // String[0] = A+ (95%) [133/140] -- display string
            // String[1] = 95 -- raw percentage for sorting
            // String[2] = 1 -- '1' if an override, '0' if calculated
            String[] gradeData = new String[3];

            GbCourseGrade gbCourseGrade = studentGradeInfo.getCourseGrade();
            CourseGrade courseGrade = gbCourseGrade.getCourseGrade();

            gradeData[0] = gbCourseGrade.getDisplayString();

            if (StringUtils.isNotBlank(courseGrade.getEnteredGrade())) {
                gradeData[2] = "1";
            } else {
                gradeData[2] = "0";
            }

            if (StringUtils.isNotBlank(courseGrade.getEnteredGrade())) {
                Double mappedGrade = courseGradeMap.get(courseGrade.getEnteredGrade());
                if(mappedGrade == null) {
                    mappedGrade = new Double(0);
                }
                gradeData[1] = FormatHelper.formatDoubleToDecimal(mappedGrade);
            } else {
                if (courseGrade.getPointsEarned() == null) {
                    gradeData[1] = "0";
                } else {
                    gradeData[1] = courseGrade.getCalculatedGrade();
                }
            }

            result.add(gradeData);
        }

        return result;
    }


    private List<Score> gradeList() {
        List<Score> result = new ArrayList<Score>();

        for (GbStudentGradeInfo studentGradeInfo : this.studentGradeInfoList) {
            for (ColumnDefinition column : this.columns) {
                Score grade = column.getValueFor(studentGradeInfo, isInstructor());
                result.add(grade);
            }
        }

        return result;

    }

    private String getString(String key) {
        return parent.getString(key);
    }

    private List<StudentDefinition> loadStudents(List<GbStudentGradeInfo> studentInfo) {
        List<StudentDefinition> result = new ArrayList<StudentDefinition>();

        for (GbStudentGradeInfo student : studentInfo) {
            StudentDefinition studentDefinition = new StudentDefinition();
            studentDefinition.setEid(student.getStudentEid());
            studentDefinition.setUserId(student.getStudentUuid());
            studentDefinition.setFirstName(student.getStudentFirstName());
            studentDefinition.setLastName(student.getStudentLastName());
            studentDefinition.setHasComments(formatCommentData(student));

            // The JavaScript will ultimately set this when it detects
            // concurrent edits.  Initialize to zeroo.
            StringBuilder zeroes = new StringBuilder();
            for (ColumnDefinition column : this.columns) {
                zeroes.append("0");
            }
            studentDefinition.setHasConcurrentEdit(zeroes.toString());

            result.add(studentDefinition);
        }

        return result;
    }

    private List<ColumnDefinition> loadColumns(List<Assignment> assignments) {
        GradebookUiSettings userSettings = ((GradebookPage) parent.getPage()).getUiSettings();

        List<ColumnDefinition> result = new ArrayList<ColumnDefinition>();

        if (assignments.isEmpty()) {
            return result;
        }

        for (int i = 0; i < assignments.size(); i++) {
            Assignment a1 = assignments.get(i);
            Assignment a2 = ((i + 1) < assignments.size()) ? assignments.get(i + 1) : null;

            String categoryWeight = null;
            if (a1.getWeight() != null) {
                categoryWeight = FormatHelper.formatDoubleAsPercentage(a1.getWeight() * 100);
            }

            boolean counted = a1.isCounted();
            // An assignment is not counted if uncategorised and the categories are enabled
            if ((GbCategoryType.valueOf(settings.getCategoryType()) != GbCategoryType.NO_CATEGORY) &&
                a1.getCategoryId() == null) {
                counted = false;
            }
            result.add(new AssignmentDefinition(a1.getId(),
                                                a1.getName(),
                                                FormatHelper.abbreviateMiddle(a1.getName()),
                                                FormatHelper.formatDoubleToDecimal(a1.getPoints()),
                                                FormatHelper.formatDate(a1.getDueDate(), getString("label.studentsummary.noduedate")),

                                                a1.isReleased(),
                                                counted,
                                                a1.isExtraCredit(),
                                                a1.isExternallyMaintained(),
                                                a1.getExternalId(),
                                                a1.getExternalAppName(),
                                                getIconCSSForExternalAppName(a1.getExternalAppName()),

                                                nullable(a1.getCategoryId()),
                                                a1.getCategoryName(),
                                                userSettings.getCategoryColor(a1.getCategoryName()),
                                                nullable(categoryWeight),
                                                a1.isCategoryExtraCredit(),

                                                !uiSettings.isAssignmentVisible(a1.getId())));


            // If we're at the end of the assignment list, or we've just changed
            // categories, put out a total.
            if (userSettings.isGroupedByCategory() &&
                a1.getCategoryId() != null &&
                (a2 == null || !a1.getCategoryId().equals(a2.getCategoryId()))) {
                result.add(new CategoryAverageDefinition(a1.getCategoryId(),
                                                         a1.getCategoryName(),
                                                        (new StringResourceModel("label.gradeitem.categoryaverage", null, new Object[] { a1.getCategoryName() })).getString(),
                                                         nullable(categoryWeight),
                                                         a1.isCategoryExtraCredit(),
                                                         userSettings.getCategoryColor(a1.getCategoryName()),
                                                         !uiSettings.isCategoryScoreVisible(a1.getCategoryName())));
            }
        }

        // if group by categories is disabled, then show all catagory scores
        // at the end of the table
        if (!userSettings.isGroupedByCategory()) {
            for (CategoryDefinition category : categories) {
                if (!category.getAssignmentList().isEmpty()) {
                    String categoryWeight = null;
                    if (category.getWeight() != null) {
                        categoryWeight = FormatHelper.formatDoubleAsPercentage(category.getWeight() * 100);
                    }
                    result.add(new CategoryAverageDefinition(
                        category.getId(),
                        category.getName(),
                        (new StringResourceModel("label.gradeitem.categoryaverage", null, new Object[] { category.getName() })).getString(),
                        nullable(categoryWeight),
                        category.isExtraCredit(),
                        userSettings.getCategoryColor(category.getName()),
                        !uiSettings.isCategoryScoreVisible(category.getName())));
                }
            }
        }

        return result;
    }

    private String formatCommentData(GbStudentGradeInfo student) {
        StringBuilder sb = new StringBuilder();

        for (ColumnDefinition column : this.columns) {
            if (column instanceof AssignmentDefinition) {
                AssignmentDefinition assignmentColumn = (AssignmentDefinition) column;
                GbGradeInfo gradeInfo = student.getGrades().get(assignmentColumn.getAssignmentId());
                if (gradeInfo != null && !StringUtils.isBlank(gradeInfo.getGradeComment())) {
                    sb.append('1');
                } else {
                    sb.append('0');
                }
            } else {
                sb.append('0');
            }
        }

        return sb.toString();
    }

    private String nullable(Object value) {
        if (value == null) {
            return null;
        } else {
            return value.toString();
        }
    }

    private boolean isInstructor() {
        return GbRole.INSTRUCTOR.equals(role);
    }

    private abstract class Score {
        private String score;

        public Score(String score) {
            this.score = score;
        }

        abstract boolean canEdit();

        // We assume you'll check isNull() prior to calling this
        public double getScore() {
            return Double.valueOf(score);
        };

        public boolean isNull() {
            return score == null;
        }

        public boolean isLarge() {
            return score != null && Double.valueOf(score) > 16384;
        }
    }

    private class EditableScore extends Score {
        public EditableScore(String score) {
            super(score);
        }

        @Override
        public boolean canEdit() {
            return true;
        }
    }

    private class ReadOnlyScore extends Score {
        public ReadOnlyScore(String score) {
            super(score);
        }

        @Override
        public boolean canEdit() {
            return false;
        }
    }

    private String getIconCSSForExternalAppName(String externalAppName) {
        if (toolNameIconCSSMap.containsKey(externalAppName)) {
            return toolNameIconCSSMap.get(externalAppName);
        }

        return defaultIconCSS;
    }
}
