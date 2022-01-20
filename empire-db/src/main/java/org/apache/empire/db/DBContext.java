/*
 * ESTEAM Software GmbH, 19.01.2022
 */
package org.apache.empire.db;

import java.sql.Connection;

import org.apache.empire.db.context.DBRollbackHandler;

public interface DBContext
{
    DBDatabaseDriver getDriver();
    
    Connection getConnection();
    
    <T extends DBUtils> T getUtils();
    
    void commit();

    void rollback();

    void removeRollbackHandler(DBObject object);
    
    void addRollbackHandler(DBRollbackHandler handler);
    
    void discard();
    
}
