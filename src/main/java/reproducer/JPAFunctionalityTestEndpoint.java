package reproducer;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.SharedCacheMode;
import javax.persistence.TypedQuery;
import javax.persistence.ValidationMode;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceProviderResolverHolder;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.jta.TransactionManager;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.api.transaction.TransactionIntegration;
import io.agroal.narayana.NarayanaTransactionIntegration;

/**
 * Various tests convering JPA functionality. All tests should work in both standard JVM and SubstrateVM.
 */
@WebServlet(name = "JPATestBootstrapEndpoint", urlPatterns = "/jpa/testfunctionality")
public class JPAFunctionalityTestEndpoint extends HttpServlet {

    private volatile EntityManagerFactory entityManagerFactory;

    private static final String USERNAME = "hibernate_orm_test";
    private static final String PASSWORD = "hibernate_orm_test";
    private static final String URL = "jdbc:postgresql:hibernate_orm_test";


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            bootAndDoStuff();
        } catch (Exception e) {
            reportException("Oops, shit happened, No boot for you!", e, resp);
        }
        resp.getWriter().write("OK");
    }

    public void bootAndDoStuff() throws Exception {
        if (entityManagerFactory == null) {
            synchronized (JPAFunctionalityTestEndpoint.class) {
                if (entityManagerFactory == null) {

                    Class<?> providerClass = org.postgresql.Driver.class;
                    AgroalDataSourceConfigurationSupplier dataSourceConfiguration = new AgroalDataSourceConfigurationSupplier();
                    dataSourceConfiguration.connectionPoolConfiguration().connectionFactoryConfiguration().jdbcUrl(URL);
                    dataSourceConfiguration.connectionPoolConfiguration().connectionFactoryConfiguration().connectionProviderClass(providerClass);


                    TransactionIntegration txIntegration = new NarayanaTransactionIntegration(TransactionManager.transactionManager(), new TransactionSynchronizationRegistryImple(), null, true);
                    dataSourceConfiguration.connectionPoolConfiguration().transactionIntegration(txIntegration);

                    // use the name / password from the callbacks
                    dataSourceConfiguration.connectionPoolConfiguration().connectionFactoryConfiguration().principal(new NamePrincipal(USERNAME));

                    dataSourceConfiguration.connectionPoolConfiguration().connectionFactoryConfiguration().credential(new SimplePassword(PASSWORD));


                    dataSourceConfiguration.connectionPoolConfiguration().maxSize(5);
                    AgroalDataSource agroalDataSource = AgroalDataSource.from(dataSourceConfiguration);

                    PersistenceUnitInfo info = new PersistenceUnitInfo() {
                        @Override
                        public String getPersistenceUnitName() {
                            return "test";
                        }

                        @Override
                        public String getPersistenceProviderClassName() {
                            return null;
                        }

                        @Override
                        public PersistenceUnitTransactionType getTransactionType() {
                            return PersistenceUnitTransactionType.JTA;
                        }

                        @Override
                        public DataSource getJtaDataSource() {
                            return agroalDataSource;
                        }

                        @Override
                        public DataSource getNonJtaDataSource() {
                            return null;
                        }

                        @Override
                        public List<String> getMappingFileNames() {
                            return Collections.emptyList();
                        }

                        @Override
                        public List<java.net.URL> getJarFileUrls() {
                            return Collections.emptyList();
                        }

                        @Override
                        public URL getPersistenceUnitRootUrl() {
                            return null;
                        }

                        @Override
                        public List<String> getManagedClassNames() {
                            List<String> ret = new ArrayList<>();
                            ret.add(Person.class.getName());
                            ret.add(SequencedAddress.class.getName());
                            ret.add(WorkAddress.class.getName());
                            ret.add(Animal.class.getName());
                            ret.add(Human.class.getName());
                            ret.add(Address.class.getName());
                            return ret;
                        }

                        @Override
                        public boolean excludeUnlistedClasses() {
                            return false;
                        }

                        @Override
                        public SharedCacheMode getSharedCacheMode() {
                            return null;
                        }

                        @Override
                        public ValidationMode getValidationMode() {
                            return null;
                        }

                        @Override
                        public Properties getProperties() {
                            return null;
                        }

                        @Override
                        public String getPersistenceXMLSchemaVersion() {
                            return null;
                        }

                        @Override
                        public ClassLoader getClassLoader() {
                            return null;
                        }

                        @Override
                        public void addTransformer(ClassTransformer transformer) {

                        }

                        @Override
                        public ClassLoader getNewTempClassLoader() {
                            return null;
                        }
                    };

                    entityManagerFactory =PersistenceProviderResolverHolder.getPersistenceProviderResolver().getPersistenceProviders().get(0)

                            .createContainerEntityManagerFactory(info, new HashMap());
                }
            }
        }

        System.out.println("Hibernate EntityManagerFactory: booted");
        doStuffWithHibernate(entityManagerFactory);
    }

    /**
     * Lists the various operations we want to test for:
     */
    private static void doStuffWithHibernate(EntityManagerFactory entityManagerFactory) {

        //Cleanup any existing data:
        deleteAllPerson(entityManagerFactory);

        //Store some well known Person instances we can then test on:
        storeTestPersons(entityManagerFactory);

        //Load all persons and run some checks on the query results:
        verifyListOfExistingPersons(entityManagerFactory);

        //Try a JPA named query:
        verifyJPANamedQuery(entityManagerFactory);

        deleteAllPerson(entityManagerFactory);

    }

    private static void verifyJPANamedQuery(final EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        TypedQuery<Person> typedQuery = em.createNamedQuery(
                "get_person_by_name", Person.class
        );
        typedQuery.setParameter("name", "Shamrock");
        final Person singleResult = typedQuery.getSingleResult();

        if (!singleResult.getName().equals("Shamrock")) {
            throw new RuntimeException("Wrong result from named JPA query");
        }

        transaction.commit();
        em.close();
    }

    private static void verifyListOfExistingPersons(final EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        listExistingPersons(em);
        transaction.commit();
        em.close();
    }

    private static void storeTestPersons(final EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        persistNewPerson(em, "Protean");
        persistNewPerson(em, "Shamrock");
        persistNewPerson(em, "Hibernate ORM");
        transaction.commit();
        em.close();
    }

    private static void deleteAllPerson(final EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        em.createNativeQuery("Delete from Person").executeUpdate();
        transaction.commit();
        em.close();
    }

    private static void listExistingPersons(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Person> cq = cb.createQuery(Person.class);
        Root<Person> from = cq.from(Person.class);
        cq.select(from).orderBy(cb.asc(from.get("name")));
        TypedQuery<Person> q = em.createQuery(cq);
        List<Person> allpersons = q.getResultList();
        if (allpersons.size() != 3) {
            throw new RuntimeException("Incorrect number of results");
        }
        if (!allpersons.get(0).getName().equals("Hibernate ORM")) {
            throw new RuntimeException("Incorrect order of results");
        }
        StringBuilder sb = new StringBuilder("list of stored Person names:\n\t");
        for (Person p : allpersons) {
            p.describeFully(sb);
            sb.append("\n\t");
            if (p.getStatus() != Status.LIVING) {
                throw new RuntimeException("Incorrect status " + p);
            }
        }
        sb.append("\nList complete.\n");
        System.out.print(sb);
    }

    private static void persistNewPerson(EntityManager entityManager, String name) {
        Person person = new Person();
        person.setName(name);
        person.setStatus(Status.LIVING);
        person.setAddress(new SequencedAddress("Street " + randomName()));
        entityManager.persist(person);
    }

    private static String randomName() {
        return UUID.randomUUID().toString();
    }

    private void reportException(String errorMessage, final Exception e, final HttpServletResponse resp) throws IOException {
        final PrintWriter writer = resp.getWriter();
        if (errorMessage != null) {
            writer.write(errorMessage);
            writer.write(" ");
        }
        writer.write(e.toString());
        writer.append("\n\t");
        e.printStackTrace(writer);
        writer.append("\n\t");
    }

}
