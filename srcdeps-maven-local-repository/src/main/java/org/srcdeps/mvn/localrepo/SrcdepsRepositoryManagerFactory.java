/**
 * Copyright 2015-2018 Maven Source Dependencies
 * Plugin contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.srcdeps.mvn.localrepo;

import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.BuildService;
import org.srcdeps.core.ScmService;
import org.srcdeps.core.SrcVersion;
import org.srcdeps.core.fs.PathLocker;
import org.srcdeps.mvn.config.ConfigurationProducer;

import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;

/**
 * A {@link LocalRepositoryManagerFactory} that produces {@link SrcdepsLocalRepositoryManager}. This is done through
 * looking up the {@link LocalRepositoryManagerFactory} implementations visible from the present Guice module, choosing
 * the one specified in the {@code "srcdeps.repomanager.delegate.factory"} system property. If the
 * {@code "srcdeps.repomanager.delegate.factory"} system property was not set, the
 * {@value #DEFAULT_SRCDEPS_REPOMANAGER_DELAGATE_FACTORY} is used. The selected {@link LocalRepositoryManagerFactory}
 * implementation is then used as a delegate.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@Named("srcdeps")
public class SrcdepsRepositoryManagerFactory implements LocalRepositoryManagerFactory {
    public static final String DEFAULT_SRCDEPS_REPOMANAGER_DELAGATE_FACTORY = TakariLocalRepositoryManagerFactory.class
            .getName();
    public static final float DEFAULT_SRCDEPS_REPOMANAGER_PRIORITY = 30;
    private static final Logger log = LoggerFactory.getLogger(SrcdepsRepositoryManagerFactory.class);
    public static final String SRCDEPS_REPOMANAGER_DELAGATE_FACTORY = "srcdeps.repomanager.delegate.factory";
    public static final String SRCDEPS_REPOMANAGER_PRIORITY = "srcdeps.repomanager.priority";

    /** Passed to {@link SrcdepsLocalRepositoryManager} */
    @Inject
    private BuildService buildService;
    @Inject
    private ConfigurationProducer configurationProducer;

    /** See {@link #lookupDelegate()} */
    @Inject
    private Provider<Map<String, LocalRepositoryManagerFactory>> factories;

    /** Passed to {@link SrcdepsLocalRepositoryManager} */
    @Inject
    private PathLocker<SrcVersion> pathLocker;

    private final String preferedDelegateFactoryName;

    private final float priority;

    @Inject
    private ScmService scmService;

    public SrcdepsRepositoryManagerFactory() {
        this.priority = Float.parseFloat(
                System.getProperty(SRCDEPS_REPOMANAGER_PRIORITY, String.valueOf(DEFAULT_SRCDEPS_REPOMANAGER_PRIORITY)));
        this.preferedDelegateFactoryName = System.getProperty(SRCDEPS_REPOMANAGER_DELAGATE_FACTORY,
                DEFAULT_SRCDEPS_REPOMANAGER_DELAGATE_FACTORY);
    }

    /**
     * Returns the priority passed in {@value #SRCDEPS_REPOMANAGER_PRIORITY} system property or the default of
     * {@value #DEFAULT_SRCDEPS_REPOMANAGER_PRIORITY}.
     *
     * @see org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory#getPriority()
     */
    @Override
    public float getPriority() {
        return priority;
    }

    /**
     * Looks up the {@link LocalRepositoryManagerFactory} specified in {@link #preferedDelegateFactoryName}.
     *
     * @return the delegate factory
     * @throws IllegalStateException
     *             if the {@link #preferedDelegateFactoryName} was not found in the list of available
     *             {@link LocalRepositoryManagerFactory} implementations.
     */
    private LocalRepositoryManagerFactory lookupDelegate() {
        Map<String, LocalRepositoryManagerFactory> factoryImpls = factories.get();
        log.debug("srcdeps: SrcdepsRepositoryManagerFactory got [{}] LocalRepositoryManagerFactory instances",
                factoryImpls.size());

        for (Entry<String, LocalRepositoryManagerFactory> en : factoryImpls.entrySet()) {
            LocalRepositoryManagerFactory factory = en.getValue();

            log.debug("srcdeps: SrcdepsRepositoryManagerFactory iterating over LocalRepositoryManagerFactory [{}]: [{}]",
                    en.getKey(), factory.getClass().getName());

            String factoryClassName = factory.getClass().getName();
            if (factoryClassName.equals(preferedDelegateFactoryName)) {
                log.info("srcdeps: SrcdepsLocalRepositoryManager will decorate [{}]", factoryClassName);
                return factory;
            }
        }

        throw new IllegalStateException(String.format(
                "Could not find [%s] in the list of available LocalRepositoryManagerFactory implementations",
                preferedDelegateFactoryName));

    }

    /**
     * Looks up the delegate using {@link #lookupDelegate()}, calls
     * {@link SrcdepsRepositoryManagerFactory#newInstance(RepositorySystemSession, LocalRepository)} on the delegate
     * producing a delegate {@link LocalRepositoryManager} that is passed to
     * {@link SrcdepsLocalRepositoryManager#SrcdepsLocalRepositoryManager(LocalRepositoryManager, Provider, BuildService)}.
     * The new {@link SrcdepsLocalRepositoryManager} instance is then returned.
     *
     * @see org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory#newInstance(org.eclipse.aether.RepositorySystemSession,
     *      org.eclipse.aether.repository.LocalRepository)
     */
    @Override
    public LocalRepositoryManager newInstance(RepositorySystemSession session, LocalRepository repository)
            throws NoLocalRepositoryManagerException {
        LocalRepositoryManagerFactory delegate = lookupDelegate();

        log.debug("srcdeps: Creating a new SrcdepsLocalRepositoryManager");
        return new SrcdepsLocalRepositoryManager(delegate.newInstance(session, repository), buildService, scmService,
                pathLocker, configurationProducer);
    }
}
