package vladmihalcea;

import org.hibernate.LazyInitializationException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import vladmihalcea.hibernate.model.store.Company;
import vladmihalcea.hibernate.model.store.Image;
import vladmihalcea.hibernate.model.store.Product;
import vladmihalcea.hibernate.model.store.WarehouseProductInfo;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:spring/applicatonContext.xml"})
public class HibernateEqualsHashCodeTest {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateEqualsHashCodeTest.class);

    @PersistenceContext(unitName = "testPersistenceUnit")
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    private TransactionTemplate transactionTemplate;

    @PostConstruct
    private void init() {
        transactionTemplate = new TransactionTemplate(platformTransactionManager);
    }

    @Before
    public void beforeTest() {
        clean();
    }

    @Test
    public void testRootObjects() {

        final Company newCompany = new Company();
        newCompany.setName("TV Company");

        final Long companyId = transactionTemplate.execute(new TransactionCallback<Long>() {
            @Override
            public Long doInTransaction(TransactionStatus transactionStatus) {

                entityManager.persist(newCompany);
                return newCompany.getId();
            }
        });
        Company detachedCompany = transactionTemplate.execute(new TransactionCallback<Company>() {
            @Override
            public Company doInTransaction(TransactionStatus transactionStatus) {
                Company attachedCompany = entityManager.find(Company.class, companyId);
                assertEquals(newCompany, attachedCompany);
                assertEquals(newCompany.hashCode(), attachedCompany.hashCode());
                return attachedCompany;
            }
        });

        assertEquals(newCompany, detachedCompany);
        assertEquals(newCompany.hashCode(), detachedCompany.hashCode());

        transactionTemplate.execute(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction(TransactionStatus transactionStatus) {
                Company attachedCompany = entityManager.find(Company.class, companyId);
                attachedCompany.setName("New Company");
                entityManager.flush();
                return null;
            }
        });

        transactionTemplate.execute(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction(TransactionStatus transactionStatus) {
                Company attachedCompany = entityManager.find(Company.class, companyId);
                assertEquals(newCompany, attachedCompany);
                assertEquals(newCompany.hashCode(), attachedCompany.hashCode());
                return null;
            }
        });
    }

    @Test
    public void testChildObjects() {

        final Long productId = transactionTemplate.execute(new TransactionCallback<Long>() {
            @Override
            public Long doInTransaction(TransactionStatus transactionStatus) {

                Company company = new Company();
                company.setName("TV Company");
                entityManager.persist(company);

                Product product = new Product("tvCode");
                product.setName("TV");
                product.setCompany(company);

                Image frontImage = new Image();
                frontImage.setName("front image");
                frontImage.setIndex(0);

                Image sideImage = new Image();
                sideImage.setName("side image");
                sideImage.setIndex(1);

                product.addImage(frontImage);
                product.addImage(sideImage);

                WarehouseProductInfo warehouseProductInfo = new WarehouseProductInfo();
                warehouseProductInfo.setQuantity(101);
                product.addWarehouse(warehouseProductInfo);

                entityManager.persist(product);
                return product.getId();
            }
        });

        Product product = transactionTemplate.execute(new TransactionCallback<Product>() {
            @Override
            public Product doInTransaction(TransactionStatus transactionStatus) {
                return entityManager.createQuery(
                        "select p " +
                        "from Product p " +
                        "left join fetch p.images i " +
                        "where p.id = :productId", Product.class)
                        .setParameter("productId", productId)
                        .getSingleResult();
            }
        });
        Image frontImage = new Image();
        frontImage.setName("front image");
        frontImage.setProduct(product);
        frontImage.setIndex(0);
        assertTrue(product.getImages().contains(frontImage));

        List<Image> images = transactionTemplate.execute(new TransactionCallback<List<Image>>() {
            @Override
            public List<Image> doInTransaction(TransactionStatus transactionStatus) {
                return entityManager.createQuery(
                        "select i from Image i ", Image.class)
                        .getResultList();
            }
        });
        try {
            assertTrue(new HashSet<Image>(images).contains(frontImage));
            fail("Should have thrown LazyInitializationException!");
        } catch (LazyInitializationException expected) {

        }

    }

    protected void clean() {
        transactionTemplate.execute(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction(TransactionStatus transactionStatus) {
                entityManager.createQuery("delete from Version where id > 0").executeUpdate();
                entityManager.createQuery("delete from Image where id > 0").executeUpdate();
                entityManager.createQuery("delete from Product where id > 0").executeUpdate();
                entityManager.createQuery("delete from Company where id > 0").executeUpdate();
                return null;
            }
        });
    }
}
