/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.causalclustering.catchup.storecopy;

import org.apache.commons.compress.utils.Charsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.neo4j.causalclustering.catchup.CatchUpClient;
import org.neo4j.causalclustering.catchup.CatchupAddressProvider;
import org.neo4j.causalclustering.catchup.CatchupClientBuilder;
import org.neo4j.causalclustering.catchup.CatchupServerBuilder;
import org.neo4j.causalclustering.catchup.CatchupServerProtocol;
import org.neo4j.causalclustering.net.Server;
import org.neo4j.helpers.AdvertisedSocketAddress;
import org.neo4j.helpers.ListenSocketAddress;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.logging.FormattedLogProvider;
import org.neo4j.logging.Level;
import org.neo4j.logging.LogProvider;
import org.neo4j.ports.allocation.PortAuthority;
import org.neo4j.test.rule.TestDirectory;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class StoreCopyClientIT
{
    private FileSystemAbstraction fsa = new DefaultFileSystemAbstraction();
    private final LogProvider logProvider = FormattedLogProvider.withDefaultLogLevel( Level.DEBUG ).toOutputStream( System.out );
    private final TerminationCondition defaultTerminationCondition = TerminationCondition.CONTINUE_INDEFINITELY;

    @Rule
    public TestDirectory testDirectory = TestDirectory.testDirectory( fsa );

    private StoreCopyClient subject;
    private FakeFile fileA = new FakeFile( "fileA", "This is file a content" );
    private FakeFile fileB = new FakeFile( "another-file-b", "Totally different content 123" );

    private FakeFile indexFileA = new FakeFile( "lucene", "Lucene 123" );
    private Server catchupServer;
    private TestCatchupServerHandler serverHandler;

    private static void writeContents( FileSystemAbstraction fileSystemAbstraction, File file, String contents )
    {
        byte[] bytes = contents.getBytes();
        try ( StoreChannel storeChannel = fileSystemAbstraction.create( file ) )
        {
            storeChannel.write( ByteBuffer.wrap( bytes ) );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    @Before
    public void setup()
    {
        serverHandler = new TestCatchupServerHandler( logProvider, new CatchupServerProtocol(), testDirectory, fsa );
        serverHandler.addFile( fileA );
        serverHandler.addFile( fileB );
        serverHandler.addIndexFile( indexFileA );
        writeContents( fsa, relative( fileA.getFilename() ), fileA.getContent() );
        writeContents( fsa, relative( fileB.getFilename() ), fileB.getContent() );
        writeContents( fsa, relative( indexFileA.getFilename() ), indexFileA.getContent() );

        ListenSocketAddress listenAddress = new ListenSocketAddress( "localhost", PortAuthority.allocatePort() );
        catchupServer = new CatchupServerBuilder( protocol -> serverHandler ).listenAddress( listenAddress ).build();
        catchupServer.start();

        CatchUpClient catchUpClient = new CatchupClientBuilder().build();
        catchUpClient.start();
        subject = new StoreCopyClient( catchUpClient, logProvider );
    }

    @After
    public void shutdown()
    {
        catchupServer.stop();
    }

    @Test
    public void canPerformCatchup() throws StoreCopyFailedException, IOException
    {
        // given remote node has a store
        // and local client has a store
        InMemoryFileSystemStream storeFileStream = new InMemoryFileSystemStream();

        // when catchup is performed for valid transactionId and StoreId
        CatchupAddressProvider catchupAddressProvider = CatchupAddressProvider.fromSingleAddress( from( catchupServer.address().getPort() ) );
        subject.copyStoreFiles( catchupAddressProvider, serverHandler.getStoreId(), storeFileStream, () -> defaultTerminationCondition );

        // then the catchup is successful
        Set<String> expectedFiles = new HashSet<>( Arrays.asList( fileA.getFilename(), fileB.getFilename(), indexFileA.getFilename() ) );
        assertEquals( expectedFiles, storeFileStream.filesystem.keySet() );
        assertEquals( fileContent( relative( fileA.getFilename() ) ), clientFileContents( storeFileStream, fileA.getFilename() ) );
        assertEquals( fileContent( relative( fileB.getFilename() ) ), clientFileContents( storeFileStream, fileB.getFilename() ) );
    }

    @Test
    public void failedFileCopyShouldRetry() throws StoreCopyFailedException, IOException
    {
        // given a file will fail twice before succeeding
        fileB.setRemainingFailed( 2 );

        // and remote node has a store
        // and local client has a store
        InMemoryFileSystemStream clientStoreFileStream = new InMemoryFileSystemStream();

        // when catchup is performed for valid transactionId and StoreId
        CatchupAddressProvider catchupAddressProvider = CatchupAddressProvider.fromSingleAddress( from( catchupServer.address().getPort() ) );
        subject.copyStoreFiles( catchupAddressProvider, serverHandler.getStoreId(), clientStoreFileStream, () -> defaultTerminationCondition );

        // then the catchup is successful
        Set<String> expectedFiles = new HashSet<>( Arrays.asList( fileA.getFilename(), fileB.getFilename(), indexFileA.getFilename() ) );
        assertEquals( expectedFiles, clientStoreFileStream.filesystem.keySet() );

        // and
        assertEquals( fileContent( relative( fileA.getFilename() ) ), clientFileContents( clientStoreFileStream, fileA.getFilename() ) );
        assertEquals( fileContent( relative( fileB.getFilename() ) ), clientFileContents( clientStoreFileStream, fileB.getFilename() ) );

        // and verify server had exactly 2 failed calls before having a 3rd succeeding request
        assertEquals( 3, serverHandler.getRequestCount( fileB.getFilename() ) );

        // and verify server had exactly 1 call for all other files
        assertEquals( 1, serverHandler.getRequestCount( fileA.getFilename() ) );
    }

    @Test
    public void reconnectingWorks() throws StoreCopyFailedException, IOException
    {
        // given a remote catchup will fail midway
        // and local client has a store
        InMemoryFileSystemStream storeFileStream = new InMemoryFileSystemStream();

        // and file B is broken once (after retry it works)
        fileB.setRemainingNoResponse( 1 );

        // when catchup is performed for valid transactionId and StoreId
        CatchupAddressProvider catchupAddressProvider = CatchupAddressProvider.fromSingleAddress( from( catchupServer.address().getPort() ) );
        subject.copyStoreFiles( catchupAddressProvider, serverHandler.getStoreId(), storeFileStream, () -> defaultTerminationCondition );

        // then the catchup is possible to complete
        assertEquals( fileContent( relative( fileA.getFilename() ) ), clientFileContents( storeFileStream, fileA.getFilename() ) );
        assertEquals( fileContent( relative( fileB.getFilename() ) ), clientFileContents( storeFileStream, fileB.getFilename() ) );

        // and verify file was requested more than once
        assertThat( serverHandler.getRequestCount( fileB.getFilename() ), greaterThan( 1 ) );
    }

    private static AdvertisedSocketAddress from( int port )
    {
        return new AdvertisedSocketAddress( "localhost", port );
    }

    private File relative( String filename )
    {
        return testDirectory.file( filename );
    }

    private String fileContent( File file ) throws IOException
    {
        return fileContent( file, fsa );
    }

    static String fileContent( File file, FileSystemAbstraction fsa ) throws IOException
    {
        int chunkSize = 128;
        StringBuilder stringBuilder = new StringBuilder();
        try ( Reader reader = fsa.openAsReader( file, Charsets.UTF_8 ) )
        {
            CharBuffer charBuffer = CharBuffer.wrap( new char[chunkSize] );
            while ( reader.read( charBuffer ) != -1 )
            {
                charBuffer.flip();
                stringBuilder.append( charBuffer );
                charBuffer.clear();
            }
        }
        return stringBuilder.toString();
    }

    private String clientFileContents( InMemoryFileSystemStream storeFileStreams, String filename )
    {
        return storeFileStreams.filesystem.get( filename ).toString();
    }
}
