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

package de.jetwick.config;

import com.google.inject.AbstractModule;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;
import de.jetwick.data.TagDao;
import de.jetwick.data.UserDao;
import de.jetwick.hib.HibernateUtil;
import de.jetwick.hib.TagDaoHib;
import de.jetwick.hib.UserDaoHib;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibModule extends AbstractModule {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Configuration config;

    public HibModule() {
    }

    public HibModule(Configuration config) {
        this.config = config;
    }

    @Override
    protected void configure() {
//        bind(Session.class).toProvider(HibProvider.class);

        // http://markmail.org/message/zy3almcp5rvpsaf6 ->
        // you need to use UnitOfWork.REQUEST instead (it doesn't matter that
        // it runs outside an HTTP request).The reason is you don't really need
        // workmanager if you use UOW.TRANSACTION, any @Transactional method
        // will start and end its own unit of work in the latter case (quartz initiated or otherwise).

        // http://www.wideplay.com/webextensions%3A%3Ajpaintegration2
        // http://code.google.com/p/warp-persist/w/list
        // => either use REQUEST     + workmanager + own transactions
        //    OR     use TRANSACTION + @Transactional without workmanager
        install(PersistenceService.usingHibernate().across(UnitOfWork.REQUEST).buildModule());

        if (config == null)
            config = HibernateUtil.getConfiguration();

        // If UnitOfWork is TRANSACTION =>
//        config.setProperty("hibernate.transaction.factory_class", "org.hibernate.transaction.JDBCTransactionFactory");
//        config.setProperty("hibernate.current_session_context_class", "thread");

        // If UnitOfWork is REQUEST =>
        config.setProperty("hibernate.current_session_context_class", "managed");

        // configure via log4j instead:
        //config.setProperty("hibernate.show_sql", "true").

        bind(Configuration.class).toInstance(config);

        bind(TagDao.class).to(TagDaoHib.class);
        bind(UserDao.class).to(UserDaoHib.class);        
    }
}
