/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.empire.db;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.empire.commons.ObjectUtils;
import org.apache.empire.commons.StringUtils;
import org.apache.empire.db.exceptions.InvalidKeyException;
import org.apache.empire.db.exceptions.NoPrimaryKeyException;
import org.apache.empire.db.exceptions.QueryNoResultException;
import org.apache.empire.db.exceptions.RecordNotFoundException;
import org.apache.empire.db.exceptions.RecordUpdateFailedException;
import org.apache.empire.db.exceptions.RecordUpdateInvalidException;
import org.apache.empire.db.expr.compare.DBCompareColExpr;
import org.apache.empire.db.expr.compare.DBCompareExpr;
import org.apache.empire.db.expr.join.DBColumnJoinExpr;
import org.apache.empire.db.expr.join.DBJoinExpr;
import org.apache.empire.exceptions.InvalidArgumentException;
import org.apache.empire.exceptions.ItemNotFoundException;
import org.apache.empire.exceptions.NotImplementedException;
import org.apache.empire.exceptions.NotSupportedException;


/**
 * This class can be used to wrap a query from a DBCommand and use it like a DBRowSet.<BR>
 * You may use this class for two purposes:
 * <UL>
 *  <LI>In oder to define subqueries simply define a command object with the subquery and wrap it inside a DBQuery.
 *    Then in a second command object you can reference this Query to join with your other tables and views.
 *    In order to join other columns with your query use findQueryColumn(DBColumnExpr expr) to get the 
 *    query column object for a given column expression in the original select clause.</LI> 
 *  <LI>With a key supplied you can have an updateable query that will update several records at once.</LI>
 * </UL>
 *
 */
public class DBQuery extends DBRowSet
{
    private final static long serialVersionUID = 1L;

    private static AtomicInteger queryCount = new AtomicInteger(0);

    protected DBCommandExpr   cmdExpr;
    protected DBColumn[]      keyColumns = null;
    protected DBQueryColumn[] queryColumns = null;
    protected String          alias;

    /**
     * Constructor initializes the query object.
     * Saves the columns and the primary keys of this query.
     * 
     * @param cmd the SQL-Command
     * @param keyColumns an array of the primary key columns
     * @param the query alias
     */
    public DBQuery(DBCommandExpr cmd, DBColumn[] keyColumns, String alias)
    { // Set the column expressions
        super(cmd.getDatabase());
        this.cmdExpr = cmd;
        // Set Query Columns
        DBColumnExpr[] exprList = cmd.getSelectExprList();
        queryColumns = new DBQueryColumn[exprList.length];
        for (int i = 0; i < exprList.length; i++)
        {   // Init Columns 
            columns.add(exprList[i].getUpdateColumn());
            queryColumns[i] = new DBQueryColumn(this, exprList[i]);
        }
        // Set the key Column
        this.keyColumns = keyColumns;
        // set alias
        this.alias = alias;
    }

    /**
     * Constructor initializes the query object.
     * Saves the columns and the primary keys of this query.
     * 
     * @param cmd the SQL-Command
     * @param keyColumns an array of the primary key columns
     */
    public DBQuery(DBCommandExpr cmd, DBColumn[] keyColumns)
    {   // Set the column expressions
        this(cmd, keyColumns, "q" + String.valueOf(queryCount.incrementAndGet()));
    }
    
    /**
     * Constructs a new DBQuery object initialize the query object.
     * Save the columns and the primary key of this query.
     * 
     * @param cmd the SQL-Command
     * @param keyColumn the primary key column
     * @param the query alias
     */
    public DBQuery(DBCommandExpr cmd, DBColumn keyColumn, String alias)
    { // Set the column expressions
        this(cmd, new DBColumn[] { keyColumn }, alias);
    }
    
    /**
     * Constructs a new DBQuery object initialize the query object.
     * Save the columns and the primary key of this query.
     * 
     * @param cmd the SQL-Command
     * @param keyColumn the primary key column
     */
    public DBQuery(DBCommandExpr cmd, DBColumn keyColumn)
    { // Set the column expressions
        this(cmd, new DBColumn[] { keyColumn });
    }

    /**
     * Creaes a DBQuery object from a given command object.
     * 
     * @param cmd the command object representing an SQL-Command.
     * @param the query alias
     */
    public DBQuery(DBCommandExpr cmd, String alias)
    { // Set the column expressions
        this(cmd, (DBColumn[]) null, alias);
    }

    /**
     * Creaes a DBQuery object from a given command object.
     * 
     * @param cmd the command object representing an SQL-Command.
     */
    public DBQuery(DBCommandExpr cmd)
    { // Set the column expressions
        this(cmd, (DBColumn[]) null);
    }

    /**
     * returns the command from the underlying command expression or throws an exception
     * @return the command used for this query
     */
    private DBCommand getCommandFromExpression()
    {
        if (cmdExpr instanceof DBCommand)
            return ((DBCommand)cmdExpr);
        // not supported
        throw new NotSupportedException(this, "getCommand");
    }

    /**
     * returns the underlying command expression
     * @return the command used for this query
     */
    public DBCommandExpr getCommandExpr()
    {
        return cmdExpr;
    }

    /**
     * not applicable - returns null
     */
    @Override
    public String getName()
    {
        return null;
    }

    /**
     * not applicable - returns null
     */
    @Override
    public String getAlias()
    {
        return alias;
    }
    
    /**
     * Returns whether or not the table supports record updates.
     * @return true if the table allows record updates
     */
    @Override
    public boolean isUpdateable()
    {
        return (getKeyColumns()!=null);
    }

    /**
     * Gets all columns of this rowset (e.g. for cmd.select()).
     * 
     * @return all columns of this rowset
     */
    public DBQueryColumn[] getQueryColumns()
    {
        return queryColumns;
    }

    /**
     * This function searchs for equal columns given by the
     * specified DBColumnExpr object.
     * 
     * @param expr the DBColumnExpr object
     * @return the located column
     */
    public DBQueryColumn findQueryColumn(DBColumnExpr expr)
    {
        for (int i = 0; i < queryColumns.length; i++)
        {
            if (queryColumns[i].expr.equals(expr))
                return queryColumns[i];
        }
        // not found
        return null;
    }
    
    /**
     * This function searchs for a query column by name
     * 
     * @param the column name
     * @return the located column
     */
    public DBQueryColumn findQueryColumn(String name)
    {
        for (int i = 0; i < queryColumns.length; i++)
        {
            if (StringUtils.compareEqual(queryColumns[i].getName(), name, true))
                return queryColumns[i];
        }
        // not found
        return null;
    }

    /**
     * return query key columns
     */
    @Override
    public DBColumn[] getKeyColumns()
    {
        return keyColumns;
    }
    
    /**
     * Returns a array of primary key columns by a specified DBRecord object.
     * 
     * @param record the DBRecord object, contains all fields and the field properties
     * @return a array of primary key columns
     */
    @Override
    public Object[] getRecordKey(DBRecord record)
    {
        if (record == null || record.getRowSet() != this)
            throw new InvalidArgumentException("record", record);
        // get Key
        return (Object[]) record.getRowSetData();
    }

    /**
     * Adds the select SQL Command of this object to the specified StringBuilder object.
     * 
     * @param buf the SQL-Command
     * @param context the current SQL-Command context
     */
    @Override
    public void addSQL(StringBuilder buf, long context)
    {
        buf.append("(");
        buf.append(cmdExpr.getSelect());
        buf.append(")");
        // Add Alias
        if ((context & CTX_ALIAS) != 0 && alias != null)
        { // append alias
            buf.append(" ");
            buf.append(alias);
        }
    }

    /**
     * Initialize specified DBRecord object with primary key
     * columns (the Object[] keyValues).
     * 
     * @param rec the Record object
     * @param keyValues an array of the primary key columns
     */
    @Override
    public void initRecord(DBRecord rec, Object[] keyValues, boolean insert)
    {
        // Prepare
        prepareInitRecord(rec, keyValues, insert);
        // Initialize all Fields
        Object[] fields = rec.getFields();
        for (int i = 0; i < fields.length; i++)
            fields[i] = ObjectUtils.NO_VALUE;
        // Set primary key values
        if (keyValues != null)
        { // search for primary key fields
            DBColumn[] keyColumns = getKeyColumns();
            for (int i = 0; i < keyColumns.length; i++)
                if (columns.contains(keyColumns[i]))
                    fields[columns.indexOf(keyColumns[i])] = keyValues[i];
        }
        // Init
        completeInitRecord(rec);
    }
    
    /**
     * Returns an error, because it is not possible to add a record to a query.
     * 
     * @param rec the DBRecord object, contains all fields and the field properties
     * @param conn a valid database connection
     * @throws NotImplementedException because this is not implemented
     */
    @Override
    public void createRecord(DBRecord rec, Connection conn)
    {
        throw new NotImplementedException(this, "createRecord");
    }

    /**
     * Creates a select SQL-Command of the query call the InitRecord method to execute the SQL-Command.
     * 
     * @param rec the DBRecord object, contains all fields and the field properties
     * @param key an array of the primary key columns
     * @param conn a valid connection to the database.
     */
    @Override
    public void readRecord(DBRecord rec, Object[] key, Connection conn)
    {
        if (conn == null || rec == null)
            throw new InvalidArgumentException("conn|rec", null);
        DBColumn[] keyColumns = getKeyColumns();
        if (key == null || keyColumns.length != key.length)
            throw new InvalidKeyException(this, key);
        // Select
        DBCommand cmd = getCommandFromExpression();
        for (int i = 0; i < keyColumns.length; i++)
        {   // Set key column constraint
            Object value = key[i];
            if (db.isPreparedStatementsEnabled())
                value = cmd.addParam(keyColumns[i], value);
            cmd.where(keyColumns[i].is(value));
        }    
        // Read Record
        try {
            // Read Record
            readRecord(rec, cmd, conn);
            // Set RowSetData
            rec.updateComplete(key.clone());
        } catch (QueryNoResultException e) {
            // Record not found
            throw new RecordNotFoundException(this, key);
        }
    }

    /**
     * Updates a query record by creating individual update commands for each table.
     * 
     * @param rec the DBRecord object. contains all fields and the field properties
     * @param conn a valid connection to the database.
     */
    @Override
    public void updateRecord(DBRecord rec, Connection conn)
    {
        // check updateable
        if (isUpdateable()==false)
            throw new NotSupportedException(this, "updateRecord");
        // check params
        if (rec == null)
            throw new InvalidArgumentException("record", null);
        if (conn == null)
            throw new InvalidArgumentException("conn", null);
        // Has record been modified?
        if (rec.isModified() == false)
            return; // Nothing to update
        // Must have key Columns
        DBColumn[] keyColumns = getKeyColumns();
        if (keyColumns==null)
            throw new NoPrimaryKeyException(this);
        // Get the fields and the flags
        Object[] fields = rec.getFields();
        // Get all Update Commands
        Map<DBRowSet, DBCommand> updCmds = new HashMap<DBRowSet, DBCommand>(3);
        for (int i = 0; i < columns.size(); i++)
        { // get the table
            DBColumn col = columns.get(i);
            if (col == null)
                continue;
            DBRowSet table = col.getRowSet();
            DBCommand updCmd = updCmds.get(table);
            if (updCmd == null)
            { // Add a new Command
                updCmd = db.createCommand();
                updCmds.put(table, updCmd);
            }
            /*
             * if (updateTimestampColumns.contains( col ) ) { // Check the update timestamp cmd.set( col.to( DBDatabase.SYSDATE ) ); }
             */
            // Set the field Value
            boolean modified = rec.wasModified(i);
            if (modified == true)
            { // Update a field
                if (col.isReadOnly() && log.isDebugEnabled())
                    log.debug("updateRecord: Read-only column '" + col.getName() + " has been modified!");
                // Check the value
                col.validate(fields[i]);
                // Set
                updCmd.set(col.to(fields[i]));
            }
        }
        // the commands
        DBCommand cmd = getCommandFromExpression();
        Object[] keys = (Object[]) rec.getRowSetData();
        DBRowSet table= null;
        DBCommand upd = null;
        for(Entry<DBRowSet,DBCommand> entry:updCmds.entrySet())
        {
            int i = 0;
            // Iterate through options
            table = entry.getKey();
            upd = entry.getValue();
            // Is there something to update
            if (upd.set == null)
                continue; // nothing to do for this table!
            // Evaluate Joins
            for (i = 0; cmd.joins != null && i < cmd.joins.size(); i++)
            {
                DBJoinExpr jex = cmd.joins.get(i);
                if (!(jex instanceof DBColumnJoinExpr))
                    continue;
                DBColumnJoinExpr join = (DBColumnJoinExpr)jex;
                DBColumn left  = join.getLeft() .getUpdateColumn();
                DBColumn right = join.getRight().getUpdateColumn();
                if (left.getRowSet()==table && table.isKeyColumn(left))
                    if (!addJoinRestriction(upd, left, right, keyColumns, rec))
                        throw new ItemNotFoundException(left.getFullName());
                if (right.getRowSet()==table && table.isKeyColumn(right))
                    if (!addJoinRestriction(upd, right, left, keyColumns, rec))
                        throw new ItemNotFoundException(right.getFullName());
            }
            // Evaluate Existing restrictions
            for (i = 0; cmd.where != null && i < cmd.where.size(); i++)
            {
                DBCompareExpr cmp = cmd.where.get(i);
                if (cmp instanceof DBCompareColExpr)
                { 	// Check whether constraint belongs to update table
                    DBCompareColExpr cmpExpr = (DBCompareColExpr) cmp;
                    DBColumn col = cmpExpr.getColumnExpr().getUpdateColumn();
                    if (col!=null && col.getRowSet() == table)
                    {	// add the constraint
                    	if (cmpExpr.getValue() instanceof DBCmdParam)
                    	{	// Create a new command param
                    		DBColumnExpr colExpr = cmpExpr.getColumnExpr();
                    		DBCmdParam param =(DBCmdParam)cmpExpr.getValue(); 
                    		DBCmdParam value = upd.addParam(colExpr, param.getValue());
                    		cmp = new DBCompareColExpr(colExpr, cmpExpr.getCmpop(), value);
                    	}
                        upd.where(cmp);
                    }    
                } 
                else
                {	// other constraints are not supported
                    throw new NotSupportedException(this, "updateRecord with "+cmp.getClass().getName());
                }
            }
            // Add Restrictions
            for (i = 0; i < keyColumns.length; i++)
            {
                if (keyColumns[i].getRowSet() == table)
                {   // Set key column constraint
                    Object value = keys[i];
                    if (db.isPreparedStatementsEnabled())
                        value = upd.addParam(keyColumns[i], value);
                    upd.where(keyColumns[i].is(value));
                }
            }    

            // Set Update Timestamp
            int timestampIndex = -1;
            Object timestampValue = null;
            if (table.getTimestampColumn() != null)
            {
                DBColumn tsColumn = table.getTimestampColumn();
                timestampIndex = this.getColumnIndex(tsColumn);
                if (timestampIndex>=0)
                {   // The timestamp is availabe in the record
                    timestampValue = db.getUpdateTimestamp(conn); 
                    Object lastTS = fields[timestampIndex];
                    if (ObjectUtils.isEmpty(lastTS)==false)
                    {   // set timestamp constraint
                        if (db.isPreparedStatementsEnabled())
                            lastTS = upd.addParam(tsColumn, lastTS);
                        upd.where(tsColumn.is(lastTS));
                    }    
                    // Set new Timestamp
                    upd.set(tsColumn.to(timestampValue));
                }
                else
                {   // Timestamp columns has not been provided with the record
                    upd.set(tsColumn.to(DBDatabase.SYSDATE));
                }
            }
            
            // Execute SQL
            int affected = db.executeSQL(upd.getUpdate(), upd.getParamValues(), conn);
            if (affected <= 0)
            {   // Error
                if (affected == 0)
                { // Record not found
                    throw new RecordUpdateFailedException(this, keys);
                }
                // Rollback
                db.rollback(conn);
                return;
            } 
            else if (affected > 1)
            { // More than one record
                throw new RecordUpdateInvalidException(this, keys);
            } 
            else
            { // success
                log.info("Record for table '" + table.getName() + " sucessfully updated!");
            }
            // Correct Timestamp
            if (timestampIndex >= 0)
            {   // Set the correct Timestamp
                fields[timestampIndex] = timestampValue;
            }
        }
        // success
        rec.updateComplete(keys);
    }

    /**
     * Adds join restrictions to the supplied command object.
     */
    protected boolean addJoinRestriction(DBCommand upd, DBColumn updCol, DBColumn keyCol, DBColumn[] keyColumns, DBRecord rec)
    {   // Find key for foreign field
        Object rowsetData = rec.getRowSetData();
        for (int i = 0; i < keyColumns.length; i++)
            if (keyColumns[i]==keyCol && rowsetData!=null)
            {   // Set Field from Key
                upd.where(updCol.is(((Object[]) rowsetData)[i]));
                return true;
            }
        // Not found, what about the record
        int index = this.getColumnIndex(updCol);
        if (index<0)
            index = this.getColumnIndex(keyCol);
        if (index>=0)
        {   // Field Found
            if (rec.wasModified(index))
                return false; // Ooops, Key field has changed
            // Set Constraint
            upd.where(updCol.is(rec.getValue(index)));
            return true;
        }
        return false;
    }

    /**
     * Deletes a record identified by its primary key from the database.
     * 
     * @param keys array of primary key values
     * @param conn a valid database connection
     */
    @Override
    public void deleteRecord(Object[] keys, Connection conn)
    {
        throw new NotImplementedException(this, "deleteRecord()");
    }

}