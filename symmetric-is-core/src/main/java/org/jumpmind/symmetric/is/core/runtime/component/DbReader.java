package org.jumpmind.symmetric.is.core.runtime.component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang.StringUtils;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.is.core.model.SettingDefinition;
import org.jumpmind.symmetric.is.core.model.SettingDefinition.Type;
import org.jumpmind.symmetric.is.core.runtime.EntityData;
import org.jumpmind.symmetric.is.core.runtime.IExecutionTracker;
import org.jumpmind.symmetric.is.core.runtime.Message;
import org.jumpmind.symmetric.is.core.runtime.MessageManipulationStrategy;
import org.jumpmind.symmetric.is.core.runtime.StartupMessage;
import org.jumpmind.symmetric.is.core.runtime.flow.IMessageTarget;
import org.jumpmind.symmetric.is.core.runtime.resource.IResourceFactory;
import org.jumpmind.symmetric.is.core.runtime.resource.ResourceCategory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.util.LinkedCaseInsensitiveMap;

@ComponentDefinition(typeName = DbReader.TYPE, category = ComponentCategory.READER,
        supports = { ComponentSupports.INPUT_MESSAGE, ComponentSupports.OUTPUT_MESSAGE,
                ComponentSupports.INPUT_MODEL, ComponentSupports.OUTPUT_MODEL },
        resourceCategory = ResourceCategory.DATASOURCE)
public class DbReader extends AbstractComponent {

    public static final String TYPE = "Database Reader";

    @SettingDefinition(order = 0, required = true, type = Type.SQL, label = "Sql")
    public final static String SQL = "db.reader.sql";

    @SettingDefinition(order = 10, required = true, type = Type.INTEGER, defaultValue = "1",
            label = "Rows/Msg")
    public final static String ROWS_PER_MESSAGE = "db.reader.rows.per.message";

    @SettingDefinition(order = 10, required = true, type = Type.BOOLEAN, defaultValue = "false",
            label = "Trim Columns")
    public final static String TRIM_COLUMNS = "db.reader.trim.columns";

    @SettingDefinition(order = 200, type = Type.CHOICE, choices = { "REPLACE", "ENHANCE" },
            defaultValue = "REPLACE", label = "Msg Strategy")
    public final static String MESSAGE_MANIPULATION_STRATEGY = "db.reader.message.manipulation.strategy";

    String sql;
    long rowsPerMessage;
    MessageManipulationStrategy messageManipulationStrategy = MessageManipulationStrategy.REPLACE;
    boolean trimColumns = false;

    @Override
    public void start(IExecutionTracker executionTracker, IResourceFactory resourceFactory) {
        super.start(executionTracker, resourceFactory);
        applySettings();
    }

    @Override
    public void handle(final Message inputMessage, final IMessageTarget messageTarget) {

        NamedParameterJdbcTemplate template = getJdbcTemplate();
        Map<String, Object> paramMap = new HashMap<String, Object>();

        int inboundRecordCount = 1;
        ArrayList<EntityData> payload = null;
        if (!(inputMessage instanceof StartupMessage)) {
            payload = inputMessage.getPayload();
            inboundRecordCount = payload.size();
        }

        /*
         * A reader can be started by a startup message (if it has no input
         * links) or it can be started by another component that sends messages
         * to it. If the reader is started by another component, then loop for
         * all records in the input message
         */
        for (int i = 0; i < inboundRecordCount; i++) {
            if (payload != null && payload.size() > i) {
                setParamsFromInboundMsgAndRec(paramMap, inputMessage, payload.get(i));
            } else {
                setParamsFromInboundMsgAndRec(paramMap, inputMessage, null);
            }
            template.query(sql, paramMap, new ResultSetExtractor<Object>() {
                @Override
                public Object extractData(ResultSet rs) throws SQLException, DataAccessException {
                    Map<Integer, String> sqlEntityHints = getSqlColumnEntityHints(sql);
                    ResultSetMetaData meta = rs.getMetaData();
                    int count = meta.getColumnCount();

                    Message message = null;
                    int outputRecCount = 0;
                    while (rs.next()) {
                        if (message == null) {
                            if (messageManipulationStrategy == MessageManipulationStrategy.ENHANCE) {
                                message = inputMessage.copy();
                            } else {
                                message = new Message(flowStep.getId());
                                message.setPayload(new ArrayList<EntityData>());
                            }
                        }
                        Map<String, EntityData> records = new LinkedCaseInsensitiveMap<EntityData>(
                                1);
                        for (int i = 1; i <= count; i++) {
                            String columnName = meta.getColumnName(i);
                            String tableName = meta.getTableName(i);
                            if (sqlEntityHints.containsKey(i)) {
                                String hint = sqlEntityHints.get(i);
                                if (hint.indexOf(".") != -1) {
                                    tableName = hint.substring(0, hint.indexOf("."));
                                    columnName = hint.substring(hint.indexOf(".") + 1);
                                } else {
                                    tableName = hint;
                                }
                            }
                            if (StringUtils.isBlank(tableName)) {
                                throw new SQLException(
                                        "The table name could not be determined while mapping a database record to an EntitiesRow. "
                                                + "Try using hints to specify a column's table name as part of the SQL query.");
                            }
                            if (outputRecCount == 0) {
                                checkTableAndColumnAgainstOutputModel(tableName, columnName);
                            }
                            EntityData record = records.get(tableName);
                            if (record == null) {
                                record = new EntityData(tableName);
                                records.put(tableName, record);
                            }

                            Object value = JdbcUtils.getResultSetValue(rs, i);
                            if (trimColumns && value instanceof String) {
                                value = value.toString().trim();
                            }
                            record.put(columnName, value);
                        }

                        ArrayList<EntityData> payload = message.getPayload();
                        payload.addAll(records.values());

                        if (payload.size() >= rowsPerMessage) {
                            messageTarget.put(message);
                            message = null;
                        }
                        outputRecCount++;
                    } // loop for resultset for a given query
                    rs.close();
                    if (message != null) {
                        messageTarget.put(message);
                    }
                    return null;
                } /* end while for each result set msg from query */
            });
        } /* for record count within message */
    }

    private void checkTableAndColumnAgainstOutputModel(String tableName, String columnName)
            throws SQLException {
        if (!this.flowStep.getComponentVersion().getOutputModelVersion()
                .entityAttributeExists(tableName, columnName)) {
            throw new SQLException(
                    "ResultSet returned a column that was not in the output model. Table:"
                            + tableName + "Column:" + columnName);
        }
    }

    protected NamedParameterJdbcTemplate getJdbcTemplate() {

        return new NamedParameterJdbcTemplate((DataSource) this.resource.reference());
    }

    protected void setParamsFromInboundMsgAndRec(Map<String, Object> paramMap,
            final Message inputMessage, final EntityData dataRecord) {

        /*
         * input parameters can come from the header and the record. header
         * parms should be used for every record.
         */
        paramMap.clear();
        paramMap.putAll(getParamsFromHeader(inputMessage));
        if (dataRecord != null) {
            paramMap.putAll(getParamsFromDetailRecord(dataRecord));
        }
    }

    protected Map<String, Object> getParamsFromHeader(final Message inputMessage) {

        if (inputMessage != null && inputMessage.getHeader() != null) {
            Map<String, Object> paramMap = new HashMap<String, Object>(inputMessage.getHeader()
                    .getParameters());
            return paramMap;
        } else {
            return null;
        }
    }

    protected Map<String, Object> getParamsFromDetailRecord(EntityData dataRecord) {

        return dataRecord;
    }

    protected void applySettings() {
        TypedProperties properties = flowStep.getComponentVersion().toTypedProperties(this,
                false);
        sql = properties.get(SQL);
        rowsPerMessage = properties.getLong(ROWS_PER_MESSAGE);
        messageManipulationStrategy = MessageManipulationStrategy.valueOf(properties
                .get(MESSAGE_MANIPULATION_STRATEGY));
        trimColumns = properties.is(TRIM_COLUMNS);
    }

    protected Map<Integer, String> getSqlColumnEntityHints(String sql) {
        Map<Integer, String> columnEntityHints = new HashMap<Integer, String>();
        String columns = sql.substring(sql.toLowerCase().indexOf("select ") + 7, sql.toLowerCase()
                .indexOf("from "));
        int commentIdx = 0;
        while (columns.indexOf("/*", commentIdx) != -1) {
            commentIdx = columns.indexOf("/*", commentIdx) + 2;
            int columnIdx = StringUtils.countMatches(columns.substring(0, commentIdx), ",") + 1;
            String entity = StringUtils.trim(columns.substring(commentIdx,
                    columns.indexOf("*/", commentIdx)));
            columnEntityHints.put(columnIdx, entity);
        }
        return columnEntityHints;
    }
}
