/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.directory.api.ldap.model.message.BindResponse;
import org.apache.directory.api.ldap.model.message.extended.NoticeOfDisconnect;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory;
import org.apache.directory.server.core.partition.impl.avl.AvlPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.LdapSession;
import org.apache.directory.server.ldap.LdapSessionManager;
import org.apache.directory.server.ldap.handlers.request.BindRequestHandler;
import org.apache.directory.server.ldap.handlers.response.BindResponseHandler;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.mina.core.session.IoSession;

public class EmbeddedLdapServer implements AutoCloseable
{
    private static final String INSTANCE_NAME = "sevenSeas";

    private DirectoryService _directoryService;
    private LdapServer _ldapService;

    private int _boundPort;

    public EmbeddedLdapServer(final String baseDn) throws Exception
    {
        init(baseDn);
    }

    private void init(final String baseDn) throws Exception
    {
        DefaultDirectoryServiceFactory factory = new DefaultDirectoryServiceFactory();
        factory.init(INSTANCE_NAME);

        _directoryService = factory.getDirectoryService();
        _directoryService.getChangeLog().setEnabled(false);

        File dir =  Files.createTempDirectory(INSTANCE_NAME).toFile();
        InstanceLayout il = new InstanceLayout(dir);
        _directoryService.setInstanceLayout(il);

        AvlPartition partition = new AvlPartition(_directoryService.getSchemaManager());
        partition.setId(INSTANCE_NAME);
        partition.setSuffixDn(new Dn(_directoryService.getSchemaManager(), baseDn));
        _directoryService.addPartition(partition);


        _ldapService = new LdapServer();
        _ldapService.setTransports(new TcpTransport(0));
        _ldapService.setDirectoryService(_directoryService);

        _ldapService.setBindHandlers(new BindRequestHandler(), new BindResponseHandler()
        {
            @Override
            public void handle(final LdapSession ldapSession, final BindResponse bindResponse)
            {
                // Produce a 1.3.6.1.4.1.1466.20036 (notice of disconnect).
                final IoSession ioSession = ldapSession.getIoSession();
                final LdapSessionManager ldapSessionManager = ldapSession.getLdapServer().getLdapSessionManager();
                ioSession.write(NoticeOfDisconnect.UNAVAILABLE);
                ldapSessionManager.removeLdapSession(ioSession);
            }
        });
   }

    public int getBoundPort()
    {
        return _boundPort;
    }

    public void start() throws Exception
    {
        if (_ldapService.isStarted())
        {
            throw new IllegalStateException("Service already running");
        }

        _directoryService.startup();
        _ldapService.start();
        _boundPort = ((InetSocketAddress) _ldapService.getTransports()[0].getAcceptor().getLocalAddress()).getPort();

    }

    public void applyLdif(final String ldif) throws Exception
    {
        File tmp = File.createTempFile("test", ".ldif");
        Files.copy(Thread.currentThread().getContextClassLoader().getResourceAsStream(ldif), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);

        LdifFileLoader ldifFileLoader = new LdifFileLoader(_directoryService.getAdminSession(), tmp.getAbsolutePath());
        int rv = ldifFileLoader.execute();
        if (rv == 0)
        {
            throw new IllegalStateException(String.format("Load no entries from LDIF resource : '%s'", ldif));
        }
    }

    @Override
    public void close() throws Exception
    {
        if (!_ldapService.isStarted())
        {
            throw new IllegalStateException("Service is not running");
        }

        try
        {
            _ldapService.stop();
        }
        finally
        {
            _directoryService.shutdown();
        }
    }
}
