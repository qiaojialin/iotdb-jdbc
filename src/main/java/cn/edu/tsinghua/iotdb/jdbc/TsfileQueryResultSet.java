package cn.edu.tsinghua.iotdb.jdbc;

import cn.edu.tsinghua.iotdb.jdbc.thrift.TSCloseOperationReq;
import cn.edu.tsinghua.iotdb.jdbc.thrift.TSCloseOperationResp;
import cn.edu.tsinghua.iotdb.jdbc.thrift.TSFetchResultsReq;
import cn.edu.tsinghua.iotdb.jdbc.thrift.TSFetchResultsResp;
import cn.edu.tsinghua.iotdb.jdbc.thrift.TSIService;
import cn.edu.tsinghua.iotdb.jdbc.thrift.TSOperationHandle;
import cn.edu.tsinghua.iotdb.jdbc.thrift.TS_SessionHandle;
import cn.edu.tsinghua.tsfile.timeseries.read.query.QueryDataSet;
import cn.edu.tsinghua.tsfile.timeseries.read.support.Field;
import cn.edu.tsinghua.tsfile.timeseries.read.support.RowRecord;
import org.apache.thrift.TException;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TsfileQueryResultSet implements ResultSet {

	private Statement statement = null;
	private String sql;
	private SQLWarning warningChain = null;
	private boolean wasNull = false;
	private boolean isClosed = false;
	private TSIService.Iface client = null;
	private TSOperationHandle operationHandle = null;
	private List<String> columnInfoList;
	private Map<String, Integer> columnInfoMap;
	private RowRecord record;
	private Iterator<RowRecord> recordItr;
	private int rowsFetched = 0;
	private int maxRows;
	private int fetchSize;
	private boolean emptyResultSet = false;
	private String operationType;
	private final String TIMESTAMP_STR = "Time";

	public TsfileQueryResultSet() {

	}

	public TsfileQueryResultSet(Statement statement, List<String> columnName, TSIService.Iface client,
								TS_SessionHandle sessionHandle, TSOperationHandle operationHandle, String sql,
								String aggregations)
			throws SQLException {
		this.statement = statement;
		this.sql = sql;
		this.columnInfoList = new ArrayList<>();
		this.columnInfoMap = new HashMap<>();
		this.client = client;
		this.operationHandle = operationHandle;
		this.columnInfoList.add(TIMESTAMP_STR);
		this.columnInfoMap.put(TIMESTAMP_STR, 0);
		int index = 1;
		for (String name : columnName) {
			columnInfoList.add(name);
			if(!columnInfoMap.containsKey(name)){
				columnInfoMap.put(name, index++);
			}
		}
		this.maxRows = statement.getMaxRows();
		this.fetchSize = statement.getFetchSize();
		this.operationHandle = operationHandle;
		this.operationType = aggregations;
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean absolute(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void afterLast() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void beforeFirst() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void cancelRowUpdates() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void clearWarnings() throws SQLException {
		warningChain = null;
	}

	@Override
	public void close() throws SQLException {
		if (isClosed)
			return;

		closeOperationHandle();
		client = null;
		isClosed = true;
	}

	private void closeOperationHandle() throws SQLException {
		try {
			if (operationHandle != null) {
				TSCloseOperationReq closeReq = new TSCloseOperationReq(operationHandle);
				TSCloseOperationResp closeResp = client.closeOperation(closeReq);
				Utils.verifySuccess(closeResp.getStatus());
			}
		} catch (SQLException e) {
			throw new SQLException("Error occurs for close opeation in server side becasuse " + e.getMessage());
		} catch (TException e) {
			throw new SQLException(
					"Error occurs when connecting to server for close operation, becasue: " + e.getMessage());
		}
	}

	@Override
	public void deleteRow() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public int findColumn(String columnName) throws SQLException {
		return columnInfoMap.get(columnName);
	}

	@Override
	public boolean first() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Array getArray(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Array getArray(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public InputStream getAsciiStream(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public InputStream getAsciiStream(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
		return getBigDecimal(findColumnNameByIndex(columnIndex));
	}

	@Override
	public BigDecimal getBigDecimal(String columnName) throws SQLException {
		return new BigDecimal(getValueByNane(columnName));
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
		MathContext mc = new MathContext(scale);
		return getBigDecimal(columnIndex).round(mc);
	}

	@Override
	public BigDecimal getBigDecimal(String columnName, int scale) throws SQLException {
		return getBigDecimal(findColumn(columnName), scale);
	}

	@Override
	public InputStream getBinaryStream(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public InputStream getBinaryStream(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Blob getBlob(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Blob getBlob(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException {
		return getBoolean(findColumnNameByIndex(columnIndex));
	}

	@Override
	public boolean getBoolean(String columnName) throws SQLException {
		String b = getValueByNane(columnName);
		if (b.trim().equalsIgnoreCase("0"))
			return false;
		if (b.trim().equalsIgnoreCase("1"))
			return true;
		return Boolean.parseBoolean(b);
	}

	@Override
	public byte getByte(int columnIndex) throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public byte getByte(String columnName) throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public byte[] getBytes(int columnIndex) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] getBytes(String columnName) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Reader getCharacterStream(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Reader getCharacterStream(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Clob getClob(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Clob getClob(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public int getConcurrency() throws SQLException {
		return ResultSet.CONCUR_READ_ONLY;
	}

	@Override
	public String getCursorName() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Date getDate(int columnIndex) throws SQLException {
		return new Date(getLong(columnIndex));
	}

	@Override
	public Date getDate(String columnName) throws SQLException {
		return getDate(findColumn(columnName));
	}

	@Override
	public Date getDate(int arg0, Calendar arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Date getDate(String arg0, Calendar arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public double getDouble(int columnIndex) throws SQLException {
		return getDouble(findColumnNameByIndex(columnIndex));
	}

	@Override
	public double getDouble(String columnName) throws SQLException {
		return Double.parseDouble(getValueByNane(columnName));
	}

	@Override
	public int getFetchDirection() throws SQLException {
		return ResultSet.FETCH_FORWARD;
	}

	@Override
	public int getFetchSize() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public float getFloat(int columnIndex) throws SQLException {
		return getFloat(findColumnNameByIndex(columnIndex));
	}

	@Override
	public float getFloat(String columnName) throws SQLException {
		return Float.parseFloat(getValueByNane(columnName));
	}

	@Override
	public int getHoldability() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public int getInt(int columnIndex) throws SQLException {
		return getInt(findColumnNameByIndex(columnIndex));
	}

	@Override
	public int getInt(String columnName) throws SQLException {
		return Integer.parseInt(getValueByNane(columnName));
	}

	@Override
	public long getLong(int columnIndex) throws SQLException {
		return getLong(findColumnNameByIndex(columnIndex));
	}

	@Override
	public long getLong(String columnName) throws SQLException {
		return Long.parseLong(getValueByNane(columnName));
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return new TsfileResultMetadata(columnInfoList, columnInfoMap, operationType);
	}

	@Override
	public Reader getNCharacterStream(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Reader getNCharacterStream(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public NClob getNClob(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public NClob getNClob(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public String getNString(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public String getNString(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Object getObject(int columnIndex) throws SQLException {
		return getObject(findColumnNameByIndex(columnIndex));
	}

	@Override
	public Object getObject(String columnName) throws SQLException {
		return getValueByNane(columnName);
	}

	@Override
	public Object getObject(int arg0, Map<String, Class<?>> arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Object getObject(String arg0, Map<String, Class<?>> arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public <T> T getObject(int arg0, Class<T> arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public <T> T getObject(String arg0, Class<T> arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Ref getRef(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Ref getRef(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public int getRow() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public RowId getRowId(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public RowId getRowId(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public SQLXML getSQLXML(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public SQLXML getSQLXML(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public short getShort(int columnIndex) throws SQLException {
		return getShort(findColumnNameByIndex(columnIndex));
	}

	@Override
	public short getShort(String columnName) throws SQLException {
		return Short.parseShort(getValueByNane(columnName));
	}

	@Override
	public Statement getStatement() throws SQLException {
		return this.statement;
	}

	@Override
	public String getString(int columnIndex) throws SQLException {
		return getString(findColumnNameByIndex(columnIndex));
	}

	@Override
	public String getString(String columnName) throws SQLException {
		return getValueByNane(columnName);
	}

	@Override
	public Time getTime(int columnIndex) throws SQLException {
		return new Time(getLong(columnIndex));
	}

	@Override
	public Time getTime(String columnName) throws SQLException {
		return getTime(findColumn(columnName));
	}

	@Override
	public Time getTime(int arg0, Calendar arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Time getTime(String arg0, Calendar arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Timestamp getTimestamp(int columnIndex) throws SQLException {
		return new Timestamp(getLong(columnIndex));
	}

	@Override
	public Timestamp getTimestamp(String columnName) throws SQLException {
		return getTimestamp(findColumn(columnName));
	}

	@Override
	public Timestamp getTimestamp(int arg0, Calendar arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public Timestamp getTimestamp(String arg0, Calendar arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public int getType() throws SQLException {
		return ResultSet.TYPE_FORWARD_ONLY;
	}

	@Override
	public URL getURL(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public URL getURL(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public InputStream getUnicodeStream(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public InputStream getUnicodeStream(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		return warningChain;
	}

	@Override
	public void insertRow() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean isAfterLast() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean isBeforeFirst() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean isClosed() throws SQLException {
		return isClosed;
	}

	@Override
	public boolean isFirst() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean isLast() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean last() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void moveToCurrentRow() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void moveToInsertRow() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean next() throws SQLException {
		if (maxRows > 0 && rowsFetched >= maxRows) {
			System.out.println("Reach max rows " + maxRows);
			return false;
		}

		if ((recordItr == null || !recordItr.hasNext()) && !emptyResultSet) {
			TSFetchResultsReq req = new TSFetchResultsReq(sql, fetchSize);
			
			try {
				TSFetchResultsResp resp = client.fetchResults(req);
				Utils.verifySuccess(resp.status);
				if (!resp.hasResultSet) {
					emptyResultSet = true;
				} else {
					QueryDataSet queryDataSet = Utils.convertQueryDataSet(resp.getQueryDataSet());
					List<RowRecord> records = new ArrayList<>();
					while (queryDataSet.hasNextRecord()) {
						RowRecord rowRecord = queryDataSet.getNextRecord();
						records.add(rowRecord);
					}
					recordItr = records.iterator();
				}
			} catch (TException e) {
				throw new SQLException("Cannot fetch result from server, because of network connection");
			}
		}

		if (emptyResultSet) {
			return false;
		}
		record = recordItr.next();
		// if(record.getDeltaObjectType() != null &&
		// record.getDeltaObjectType().equals(AGGREGATION_STR)){
		// if(columnInfo.containsKey(TIMESTAMP_STR)){
		// columnInfo.remove(TIMESTAMP_STR);
		// }
		// }
		rowsFetched++;
		return true;
	}

	@Override
	public boolean previous() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void refreshRow() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean relative(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean rowDeleted() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean rowInserted() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean rowUpdated() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void setFetchDirection(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void setFetchSize(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateArray(int arg0, Array arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateArray(String arg0, Array arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateAsciiStream(int arg0, InputStream arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateAsciiStream(String arg0, InputStream arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateAsciiStream(int arg0, InputStream arg1, int arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateAsciiStream(String arg0, InputStream arg1, int arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateAsciiStream(int arg0, InputStream arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateAsciiStream(String arg0, InputStream arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBigDecimal(int arg0, BigDecimal arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBigDecimal(String arg0, BigDecimal arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBinaryStream(int arg0, InputStream arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBinaryStream(String arg0, InputStream arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBinaryStream(int arg0, InputStream arg1, int arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBinaryStream(String arg0, InputStream arg1, int arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBinaryStream(int arg0, InputStream arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBinaryStream(String arg0, InputStream arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBlob(int arg0, Blob arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBlob(String arg0, Blob arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBlob(int arg0, InputStream arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBlob(String arg0, InputStream arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBlob(int arg0, InputStream arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBlob(String arg0, InputStream arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBoolean(int arg0, boolean arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBoolean(String arg0, boolean arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateByte(int arg0, byte arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateByte(String arg0, byte arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBytes(int arg0, byte[] arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateBytes(String arg0, byte[] arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateCharacterStream(int arg0, Reader arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateCharacterStream(String arg0, Reader arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateCharacterStream(int arg0, Reader arg1, int arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateCharacterStream(String arg0, Reader arg1, int arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateCharacterStream(int arg0, Reader arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateCharacterStream(String arg0, Reader arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateClob(int arg0, Clob arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateClob(String arg0, Clob arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateClob(int arg0, Reader arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateClob(String arg0, Reader arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateClob(int arg0, Reader arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");

	}

	@Override
	public void updateClob(String arg0, Reader arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateDate(int arg0, Date arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateDate(String arg0, Date arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateDouble(int arg0, double arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateDouble(String arg0, double arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateFloat(int arg0, float arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateFloat(String arg0, float arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateInt(int arg0, int arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateInt(String arg0, int arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateLong(int arg0, long arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateLong(String arg0, long arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNCharacterStream(int arg0, Reader arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNCharacterStream(String arg0, Reader arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNCharacterStream(int arg0, Reader arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNCharacterStream(String arg0, Reader arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNClob(int arg0, NClob arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNClob(String arg0, NClob arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNClob(int arg0, Reader arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNClob(String arg0, Reader arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNClob(int arg0, Reader arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNClob(String arg0, Reader arg1, long arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNString(int arg0, String arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNString(String arg0, String arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNull(int arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateNull(String arg0) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateObject(int arg0, Object arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateObject(String arg0, Object arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateObject(int arg0, Object arg1, int arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateObject(String arg0, Object arg1, int arg2) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateRef(int arg0, Ref arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateRef(String arg0, Ref arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateRow() throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateRowId(int arg0, RowId arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateRowId(String arg0, RowId arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateSQLXML(int arg0, SQLXML arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateSQLXML(String arg0, SQLXML arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateShort(int arg0, short arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateShort(String arg0, short arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateString(int arg0, String arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateString(String arg0, String arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateTime(int arg0, Time arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateTime(String arg0, Time arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateTimestamp(int arg0, Timestamp arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public void updateTimestamp(String arg0, Timestamp arg1) throws SQLException {
		throw new SQLException("Method not supported");
	}

	@Override
	public boolean wasNull() throws SQLException {
		return wasNull;
	}

	private void checkRecord() throws SQLException {
		if (record == null) {
			throw new SQLException("No record remains");
		}
	}
	
	private String findColumnNameByIndex(int columnIndex) throws SQLException{
		if(columnIndex >= columnInfoList.size()){
			throw new SQLException(String.format("column index %d out of range %d", columnIndex, columnInfoList.size()));
		}
		return columnInfoList.get(columnIndex);
	}

	private String getValueByNane(String columnName) throws SQLException {
		checkRecord();
		if (columnName.equals(TIMESTAMP_STR)) {
			return String.valueOf(record.getTime());
		}
		int tmp = columnInfoMap.get(columnName);
		Field field = record.fields.get(tmp - 1);
		if(field == null || field.getStringValue() == null) return null;
		return field.getStringValue();
	}
}
