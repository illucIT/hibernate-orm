/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.ValueGenerationType;
import org.hibernate.dialect.SybaseASE15Dialect;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;
import org.hibernate.tuple.AnnotationValueGeneration;
import org.hibernate.tuple.GenerationTiming;
import org.hibernate.tuple.ValueGenerator;

import org.hibernate.testing.SkipForDialect;
import org.hibernate.testing.TestForIssue;
import org.junit.Assert;
import org.junit.Test;

import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;

/**
 * @author Vlad Mihalcea
 */
@TestForIssue( jiraKey = "HHH-11096" )
@SkipForDialect(value = SybaseASE15Dialect.class, comment = "current_timestamp requires parenthesis which we don't render")
public class DatabaseCreationTimestampNullableColumnTest extends BaseEntityManagerFunctionalTestCase {

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class<?>[]{
                Person.class
        };
    }

    @Entity(name = "Person")
    public class Person {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String name;

        @Column(nullable = false)
        @FunctionCreationTimestamp
        private Date creationDate;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Date getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(Date creationDate) {
            this.creationDate = creationDate;
        }

    }

    @ValueGenerationType(generatedBy = FunctionCreationValueGeneration.class)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface FunctionCreationTimestamp {}

    public static class FunctionCreationValueGeneration
            implements AnnotationValueGeneration<FunctionCreationTimestamp> {

        @Override
        public void initialize(FunctionCreationTimestamp annotation, Class<?> propertyType) {
        }

        /**
         * Generate value on INSERT
         * @return when to generate the value
         */
        public GenerationTiming getGenerationTiming() {
            return GenerationTiming.INSERT;
        }

        /**
         * Returns null because the value is generated by the database.
         * @return null
         */
        public ValueGenerator<?> getValueGenerator() {
            return null;
        }

        /**
         * Returns true because the value is generated by the database.
         * @return true
         */
        public boolean referenceColumnInSql() {
            return true;
        }

        /**
         * Returns the database-generated value
         * @return database-generated value
         */
        public String getDatabaseGeneratedReferencedColumnValue() {
            return "current_timestamp";
        }
    }

    @Test
    public void generatesCurrentTimestamp() {
        doInJPA(this::entityManagerFactory, entityManager -> {
            Person person = new Person();
            person.setName("John Doe");
            entityManager.persist(person);

            entityManager.flush();
            Assert.assertNotNull(person.getCreationDate());
        });
    }
}