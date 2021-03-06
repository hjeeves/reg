/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*                                       
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*                                       
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.vosi.avail;

import ca.nrc.cadc.db.DBUtil;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.log4j.Logger;

/**
 * @author zhangsa
 *
 */
public class CheckDataSource implements CheckResource
{
    private static Logger log = Logger.getLogger(CheckDataSource.class);

    private String dataSourceName;
    private DataSource dataSource;
    private String testSQL;
    private boolean expectResults = true;
    private boolean rollback = false;
    private Integer expectNumRows;

    /**
     * Constructor to check a DataSource.
     * The test query should be something that always executes quickly and
     * returns a small result set, such as <code>select x from someTable limit 0</code>.
     * 
     * @param dataSource the DataSource to check
     * @param testSQL test query that should work
     */
    public CheckDataSource(DataSource dataSource, String testSQL)
    {
        this.dataSource = dataSource;
        this.testSQL = testSQL;
    }

    /**
     * Constructor to test a DataSource via JNDI name.
     *
     * @param dataSourceName JNDI name of DataSource
     * @param testSQL test query that should work
     */
    public CheckDataSource(String dataSourceName, String testSQL)
    {
        this.dataSourceName = dataSourceName;
        this.testSQL = testSQL;
    }
    
    public CheckDataSource(String dataSourceName, String testSQL, boolean expectResults)
    {
        this.dataSourceName = dataSourceName;
        this.testSQL = testSQL;
        this.expectResults = expectResults;
    }
    
    public CheckDataSource(String dataSourceName, String testSQL, int expectNumRows)
    {
        this.dataSourceName = dataSourceName;
        this.testSQL = testSQL;
        this.expectNumRows = expectNumRows;
        this.expectResults = true;
    }
    
    public CheckDataSource(String dataSourceName, String testSQL, boolean expectResults, boolean rollback)
    {
        this.dataSourceName = dataSourceName;
        this.testSQL = testSQL;
        this.expectResults = expectResults;
        this.rollback = rollback;
    }
    
    @Override
    public void check()
        throws CheckException
    {
        Connection con = null;
        Statement st = null;
        ResultSet rs = null;
        try
        {
            if (dataSource == null && dataSourceName != null)
            {
                this.dataSource = DBUtil.findJNDIDataSource(dataSourceName);
            }
            
            con = dataSource.getConnection();
            if (this.rollback)
            {
                con.setAutoCommit(false);
            }
            
            st = con.createStatement();
            if (expectResults)
            {
                log.debug("test for results");
                if (testSQL.trim().toLowerCase().startsWith("select "))
                {
                    rs = st.executeQuery(testSQL);
                    if (expectNumRows != null) {
                        int numRows = 0;
                        while ( rs.next() ) {
                            numRows++;
                        }
                        if (expectNumRows != numRows) {
                            String msg = "content fail: " + dataSourceName + " (" + testSQL + ") expected rows: " 
                                    + expectNumRows + " found: " + numRows;
                            log.warn(msg);
                            throw new CheckException(msg);
                        }
                    } else {
                        rs.next(); // just check the result set, but don't care if there are any rows
                    }
                }
                else
                {
                    st.executeUpdate(testSQL);
                }
            }
            else
            {
                st.execute(testSQL);
            }
            log.debug("test succeeded: " + dataSourceName + " (" + testSQL + ")");
        }
        catch(NamingException e)
        {
            log.warn("test failed: " + dataSourceName + " (" + testSQL + ")");
            throw new CheckException("DataSource not found: " + dataSourceName, e);
        }
        catch (SQLException e)
        {
            log.debug("test failed: " + dataSourceName + " (" + testSQL + ")", e);
            log.warn("test failed: " + dataSourceName + " (" + testSQL + ")");
            throw new CheckException("DataSource is not usable: " + dataSourceName, e);
        }
        finally
        {
            if (this.rollback && con != null)
            {
                try
                {
                    con.rollback();
                    con.setAutoCommit(true);
                }
                catch (SQLException e)
                {
                    log.error("rollback failed: " + dataSourceName + " (" + testSQL + ")", e);
                }
            }
            
            if (rs != null)
            {
                try { rs.close(); }
                catch(Throwable ignore) { }
            }
            if (st != null)
            {
                try { st.close(); }
                catch(Throwable ignore) { }
            }
            if (con != null)
            {
                try { con.close(); }
                catch(Throwable ignore) { }
            }
        }
    }
}
