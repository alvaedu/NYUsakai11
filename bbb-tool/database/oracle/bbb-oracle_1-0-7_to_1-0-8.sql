ALTER TABLE BBB_MEETING ADD (WAIT_FOR_MODERATOR CHAR(1));
ALTER TABLE BBB_MEETING ADD (MULTIPLE_SESSIONS_ALLOWED CHAR(1));
ALTER TABLE BBB_MEETING MODIFY (HOST_URL VARCHAR2(255) NULL);