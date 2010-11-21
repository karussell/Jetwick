/**
 * Copyright (C) 2010 Peter Karich <jetwick_@_pannous_._info>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.jetwick.hib;

import java.sql.Connection;
import java.sql.SQLException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.jdbc.Work;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JdbcHibernateBridge implements Work {

    private Session session;

    public JdbcHibernateBridge(Session sess) {
        session = sess;
    }

    public void run() {
        Transaction ta = null;
        try {
            ta = session.beginTransaction();
            session.doWork(this);
            ta.commit();
        } catch (Exception ex) {
            if (ta != null)
                ta.rollback();

            throw new UnsupportedOperationException(ex);
        } finally {
            session.close();
        }
    }

    @Override
    public void execute(Connection connection) throws SQLException {
    }
}
