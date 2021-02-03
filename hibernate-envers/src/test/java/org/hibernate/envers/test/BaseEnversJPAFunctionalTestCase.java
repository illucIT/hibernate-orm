/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.SystemException;

import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.transaction.internal.jta.JtaStatusHelper;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.boot.internal.EnversIntegrator;
import org.hibernate.envers.configuration.EnversSettings;
import org.hibernate.hql.spi.id.global.GlobalTemporaryTableBulkIdStrategy;
import org.hibernate.hql.spi.id.local.LocalTemporaryTableBulkIdStrategy;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.jpa.AvailableSettings;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.spi.Bootstrap;
import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;
import org.hibernate.jpa.test.PersistenceUnitDescriptorAdapter;
import org.hibernate.testing.AfterClassOnce;
import org.hibernate.testing.BeforeClassOnce;
import org.hibernate.testing.jdbc.SharedDriverManagerConnectionProviderImpl;
import org.hibernate.testing.jta.TestingJtaPlatformImpl;
import org.hibernate.testing.junit4.Helper;
import org.jboss.logging.Logger;
import org.junit.After;

/**
 * @author Strong Liu (stliu@hibernate.org)
 */
public abstract class BaseEnversJPAFunctionalTestCase extends AbstractEnversTest {

	private static final Dialect dialect = Dialect.getDialect();

	private EntityManagerFactoryBuilderImpl entityManagerFactoryBuilder;
	private StandardServiceRegistryImpl serviceRegistry;
	private SessionFactoryImplementor entityManagerFactory;

	private EntityManager em;
	private AuditReader auditReader;
	private ArrayList<EntityManager> isolatedEms = new ArrayList<EntityManager>();

	protected Dialect getDialect() {
		return dialect;
	}

	protected EntityManagerFactory entityManagerFactory() {
		return entityManagerFactory;
	}

	protected StandardServiceRegistryImpl serviceRegistry() {
		return serviceRegistry;
	}

	protected MetadataImplementor metadata() {
		return entityManagerFactoryBuilder.getMetadata();
	}

	@BeforeClassOnce
	@SuppressWarnings({"UnusedDeclaration"})
	public void buildEntityManagerFactory() throws Exception {
		log.trace( "Building EntityManagerFactory" );

		entityManagerFactoryBuilder = (EntityManagerFactoryBuilderImpl) Bootstrap.getEntityManagerFactoryBuilder(
				buildPersistenceUnitDescriptor(),
				buildSettings()
		);
		entityManagerFactory = entityManagerFactoryBuilder.build().unwrap( SessionFactoryImplementor.class );

		serviceRegistry = (StandardServiceRegistryImpl) entityManagerFactory.getServiceRegistry()
				.getParentServiceRegistry();

		afterEntityManagerFactoryBuilt();
	}

	private PersistenceUnitDescriptor buildPersistenceUnitDescriptor() {
		return new PersistenceUnitDescriptorAdapter();
	}

	private Map buildSettings() {
		Map settings = getConfig();
		addMappings( settings );

		if ( createSchema() ) {
			settings.put( org.hibernate.cfg.AvailableSettings.HBM2DDL_AUTO, "create-drop" );
			final String secondSchemaName = createSecondSchema();
			if ( StringHelper.isNotEmpty( secondSchemaName ) ) {
				if ( !(getDialect() instanceof H2Dialect) ) {
					throw new UnsupportedOperationException( "Only H2 dialect supports creation of second schema." );
				}
				Helper.createH2Schema( secondSchemaName, settings );
			}
		}

		if ( StringHelper.isNotEmpty( getAuditStrategy() ) ) {
			settings.put( EnversSettings.AUDIT_STRATEGY, getAuditStrategy() );
		}

		if ( !autoRegisterListeners() ) {
			settings.put( EnversIntegrator.AUTO_REGISTER, "false" );
		}

		settings.put( EnversSettings.USE_REVISION_ENTITY_WITH_NATIVE_ID, "false" );

		settings.put( org.hibernate.cfg.AvailableSettings.USE_NEW_ID_GENERATOR_MAPPINGS, "true" );
		settings.put( org.hibernate.cfg.AvailableSettings.DIALECT, getDialect().getClass().getName() );
		return settings;
	}

	protected Map getConfig() {
		Map<Object, Object> config = new HashMap<Object, Object>();

		config.put( AvailableSettings.LOADED_CLASSES, Arrays.asList( getAnnotatedClasses() ) );

		for ( Map.Entry<Class, String> entry : getCachedClasses().entrySet() ) {
			config.put( AvailableSettings.CLASS_CACHE_PREFIX + "." + entry.getKey().getName(), entry.getValue() );
		}

		for ( Map.Entry<String, String> entry : getCachedCollections().entrySet() ) {
			config.put( AvailableSettings.COLLECTION_CACHE_PREFIX + "." + entry.getKey(), entry.getValue() );
		}

		if ( getEjb3DD().length > 0 ) {
			ArrayList<String> dds = new ArrayList<String>();
			dds.addAll( Arrays.asList( getEjb3DD() ) );
			config.put( AvailableSettings.XML_FILE_NAMES, dds );
		}

		if ( !Environment.getProperties().containsKey( Environment.CONNECTION_PROVIDER ) ) {
			config.put( GlobalTemporaryTableBulkIdStrategy.DROP_ID_TABLES, "true" );
			config.put( LocalTemporaryTableBulkIdStrategy.DROP_ID_TABLES, "true" );
			config.put(
					org.hibernate.cfg.AvailableSettings.CONNECTION_PROVIDER,
					SharedDriverManagerConnectionProviderImpl.getInstance()
			);
		}
		addConfigOptions( config );

		return config;
	}

	@SuppressWarnings("unchecked")
	protected void addMappings(Map settings) {
		String[] mappings = getMappings();
		if ( mappings != null ) {
			settings.put( AvailableSettings.HBXML_FILES, String.join( ",", mappings ) );
		}
	}

	protected static final String[] NO_MAPPINGS = new String[0];

	protected String[] getMappings() {
		return NO_MAPPINGS;
	}

	protected void addConfigOptions(Map options) {
	}

	protected static final Class<?>[] NO_CLASSES = new Class[0];

	protected Class<?>[] getAnnotatedClasses() {
		return NO_CLASSES;
	}

	public Map<Class, String> getCachedClasses() {
		return new HashMap<Class, String>();
	}

	public Map<String, String> getCachedCollections() {
		return new HashMap<String, String>();
	}

	public String[] getEjb3DD() {
		return new String[] {};
	}

	protected void afterEntityManagerFactoryBuilt() {
	}

	protected boolean createSchema() {
		return true;
	}

	/**
	 * Feature supported only by H2 dialect.
	 *
	 * @return Provide not empty name to create second schema.
	 */
	protected String createSecondSchema() {
		return null;
	}

	protected boolean autoRegisterListeners() {
		return true;
	}

	@AfterClassOnce
	public void releaseEntityManagerFactory() {
		if ( entityManagerFactory != null && entityManagerFactory.isOpen() ) {
			entityManagerFactory.close();
		}
	}

	@After
	@SuppressWarnings({"UnusedDeclaration"})
	public void releaseUnclosedEntityManagers() {
		releaseUnclosedEntityManager( this.em );
		auditReader = null;
		for ( EntityManager isolatedEm : isolatedEms ) {
			releaseUnclosedEntityManager( isolatedEm );
		}
	}

	private void releaseUnclosedEntityManager(EntityManager em) {
		if ( em == null ) {
			return;
		}
		if ( !em.isOpen() ) {
			em = null;
			return;
		}
		if ( JtaStatusHelper.isActive( TestingJtaPlatformImpl.INSTANCE.getTransactionManager() ) ) {
			log.warn( "Cleaning up unfinished transaction" );
			try {
				TestingJtaPlatformImpl.INSTANCE.getTransactionManager().rollback();
			}
			catch (SystemException ignored) {
			}
		}
		try {
			if ( em.getTransaction().isActive() ) {
				em.getTransaction().rollback();
				log.warn( "You left an open transaction! Fix your test case. For now, we are closing it for you." );
			}
		}
		catch (IllegalStateException e) {
		}
		if ( em.isOpen() ) {
			// as we open an EM before the test runs, it will still be open if the test uses a custom EM.
			// or, the person may have forgotten to close. So, do not raise a "fail", but log the fact.
			em.close();
			log.warn( "The EntityManager is not closed. Closing it." );
		}
	}

	protected EntityManager getEntityManager() {
		return getOrCreateEntityManager();
	}

	protected EntityManager getOrCreateEntityManager() {
		if ( em == null || !em.isOpen() ) {
			em = entityManagerFactory.createEntityManager();
		}
		return em;
	}

	protected AuditReader getAuditReader() {
		if ( auditReader != null ) {
			return auditReader;
		}
		return auditReader = AuditReaderFactory.get( getOrCreateEntityManager() );
	}

	protected EntityManager createIsolatedEntityManager() {
		EntityManager isolatedEm = entityManagerFactory.createEntityManager();
		isolatedEms.add( isolatedEm );
		return isolatedEm;
	}

	protected EntityManager createIsolatedEntityManager(Map props) {
		EntityManager isolatedEm = entityManagerFactory.createEntityManager( props );
		isolatedEms.add( isolatedEm );
		return isolatedEm;
	}

	protected EntityManager createEntityManager(Map properties) {
		// always reopen a new EM and close the existing one
		if ( em != null && em.isOpen() ) {
			em.close();
		}
		em = entityManagerFactory.createEntityManager( properties );
		return em;
	}
}
