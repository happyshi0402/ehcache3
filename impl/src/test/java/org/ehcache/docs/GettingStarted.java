/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.docs;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.CacheManagerBuilder;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.CacheConfigurationBuilder;
import org.ehcache.config.EvictionVeto;
import org.ehcache.config.ResourcePools;
import org.ehcache.config.ResourcePoolsBuilder;
import org.ehcache.config.ResourceType;
import org.ehcache.config.event.CacheEventListenerConfigurationBuilder;
import org.ehcache.config.loaderwriter.writebehind.WriteBehindConfigurationBuilder;
import org.ehcache.config.persistence.CacheManagerPersistenceConfiguration;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.docs.plugs.CharSequenceSerializer;
import org.ehcache.docs.plugs.ListenerObject;
import org.ehcache.docs.plugs.LongSerializer;
import org.ehcache.docs.plugs.OddKeysEvictionVeto;
import org.ehcache.docs.plugs.SampleLoaderWriter;
import org.ehcache.docs.plugs.StringSerializer;
import org.ehcache.event.EventType;
import org.ehcache.internal.copy.ReadWriteCopier;
import org.ehcache.spi.copy.Copier;
import org.ehcache.spi.serialization.Serializer;
import org.junit.Test;

import java.io.File;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * Samples to get started with Ehcache 3
 *
 * If you add new examples, you should use tags to have them included in the README.adoc
 * You need to edit the README.adoc too to add  your new content.
 * The callouts are also used in docs/user/index.adoc
 */
@SuppressWarnings("unused")
public class GettingStarted {

  @Test
  public void cachemanagerExample() {
    // tag::cachemanagerExample[]
    CacheManager cacheManager
        = CacheManagerBuilder.newCacheManagerBuilder() // <1>
        .withCache("preConfigured",
            CacheConfigurationBuilder.newCacheConfigurationBuilder()
                .buildConfig(Long.class, String.class)) // <2>
        .build(false); // <3>
    cacheManager.init(); // <4>

    Cache<Long, String> preConfigured =
        cacheManager.getCache("preConfigured", Long.class, String.class); // <5>

    Cache<Long, String> myCache = cacheManager.createCache("myCache", // <6>
        CacheConfigurationBuilder.newCacheConfigurationBuilder().buildConfig(Long.class, String.class));

    myCache.put(1L, "da one!"); // <7>
    String value = myCache.get(1L); // <8>

    cacheManager.removeCache("preConfigured"); // <9>

    cacheManager.close(); // <10>
    // end::cachemanagerExample[]
  }

  @Test
  public void persistentCacheManager() throws Exception {
    // tag::persistentCacheManager[]
    PersistentCacheManager persistentCacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .with(new CacheManagerPersistenceConfiguration(new File(getStoragePath(), "myData"))) // <1>
        .withCache("persistent-cache", CacheConfigurationBuilder.newCacheConfigurationBuilder()
            .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder()
                .heap(10, EntryUnit.ENTRIES)
                .disk(10L, MemoryUnit.MB, true)) // <2>
            .buildConfig(Long.class, String.class))
        .build(true);

    persistentCacheManager.close();
    // end::persistentCacheManager[]
  }

  @Test
  public void offheapCacheManager() {
    // tag::offheapCacheManager[]
    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().withCache("tieredCache",
        CacheConfigurationBuilder.newCacheConfigurationBuilder()
            .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder()
                .heap(10, EntryUnit.ENTRIES)
                .offheap(10, MemoryUnit.MB)) // <1>
            .buildConfig(Long.class, String.class)).build(true);

    cacheManager.close();
    // end::offheapCacheManager[]
  }

  @Test
  public void threeTiersCacheManager() throws Exception {
    // tag::threeTiersCacheManager[]
    PersistentCacheManager persistentCacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .with(new CacheManagerPersistenceConfiguration(new File(getStoragePath(), "myData"))) // <1>
        .withCache("threeTieredCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder()
                .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder()
                        .heap(10, EntryUnit.ENTRIES) // <2>
                        .offheap(1, MemoryUnit.MB) // <3>
                        .disk(20, MemoryUnit.MB) // <4>
                )
                .buildConfig(Long.class, String.class)).build(true);

    persistentCacheManager.close();
    // end::threeTiersCacheManager[]
  }

  @Test
  public void defaultSerializers() throws Exception {
    // tag::defaultSerializers[]
    CacheConfiguration<Long, String> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .withResourcePools(ResourcePoolsBuilder
            .newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES).offheap(1, MemoryUnit.MB).build())
        .buildConfig(Long.class, String.class);

    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache("cache", cacheConfiguration)
        .withSerializer(String.class, StringSerializer.class) // <1>
        .build(true);

    Cache<Long, String> cache = cacheManager.getCache("cache", Long.class, String.class);

    cache.put(1L, "one");
    assertThat(cache.get(1L), equalTo("one"));

    cacheManager.close();
    // end::defaultSerializers[]
  }

  @Test
  public void cacheSerializers() throws Exception {
    // tag::cacheSerializers[]
    CacheConfiguration<Long, String> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES).offheap(10, MemoryUnit.MB))
        .withKeySerializer((Serializer) new LongSerializer()) // <1>
        .withValueSerializer((Serializer) new CharSequenceSerializer()) // <2>
        .buildConfig(Long.class, String.class);

    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache("cache", cacheConfiguration)
        .build(true);

    Cache<Long, String> cache = cacheManager.getCache("cache", Long.class, String.class);

    cache.put(1L, "one");
    assertThat(cache.get(1L), equalTo("one"));

    cacheManager.close();
    // end::cacheSerializers[]
  }

  @Test
  public void testCacheEventListener() {
    // tag::cacheEventListener[]
    CacheEventListenerConfigurationBuilder cacheEventListenerConfiguration = CacheEventListenerConfigurationBuilder
        .newEventListenerConfiguration(new ListenerObject(), EventType.CREATED, EventType.UPDATED) // <1>
        .unordered().asynchronous(); // <2>
    
    final CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache("foo",
            CacheConfigurationBuilder.newCacheConfigurationBuilder()
                .add(cacheEventListenerConfiguration) // <3>
                .buildConfig(String.class, String.class)).build(true);

    final Cache<String, String> cache = manager.getCache("foo", String.class, String.class);
    cache.put("Hello", "World"); // <4>
    cache.put("Hello", "Everyone"); // <5>
    cache.remove("Hello"); // <6>
    // end::cacheEventListener[]

    manager.close();
  }

  @Test
  public void writeThroughCache() throws ClassNotFoundException {
    // tag::writeThroughCache[]
    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
    
    final Cache<Long, String> writeThroughCache = cacheManager.createCache("writeThroughCache",
        CacheConfigurationBuilder.newCacheConfigurationBuilder()
            .withLoaderWriter((SampleLoaderWriter) new SampleLoaderWriter<Long, String>(singletonMap(41L, "zero"))) // <1>
            .buildConfig(Long.class, String.class));
    
    assertThat(writeThroughCache.get(41L), is("zero"));
    writeThroughCache.put(42L, "one");
    assertThat(writeThroughCache.get(42L), equalTo("one"));
    
    cacheManager.close();
    // end::writeThroughCache[]
  }
  
  @Test
  public void writeBehindCache() throws ClassNotFoundException {
    // tag::writeBehindCache[]
    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
    
    final Cache<Long, String> writeBehindCache = cacheManager.createCache("writeBehindCache",
        CacheConfigurationBuilder.newCacheConfigurationBuilder()
            .withLoaderWriter((SampleLoaderWriter) new SampleLoaderWriter<Long, String>(singletonMap(41L, "zero"))) // <1>
            .add(WriteBehindConfigurationBuilder // <2>
                .newBatchedWriteBehindConfiguration(1, TimeUnit.SECONDS, 3)// <3>
                .queueSize(3)// <4>
                .concurrencyLevel(1) // <5>
                .enableCoalescing()) // <6>
            .buildConfig(Long.class, String.class));
    
    assertThat(writeBehindCache.get(41L), is("zero"));
    writeBehindCache.put(42L, "one");
    writeBehindCache.put(43L, "two");
    writeBehindCache.put(42L, "This goes for the record");
    assertThat(writeBehindCache.get(42L), equalTo("This goes for the record"));
    
    cacheManager.close();
    // end::writeBehindCache[]  
  }

  @Test
  public void updateResourcesAtRuntime() throws InterruptedException {
    ListenerObject listener = new ListenerObject();
    CacheEventListenerConfigurationBuilder cacheEventListenerConfiguration = CacheEventListenerConfigurationBuilder
        .newEventListenerConfiguration(listener, EventType.EVICTED).unordered().synchronous();

    CacheConfiguration<Long, String> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .add(cacheEventListenerConfiguration)
        .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder()
            .heap(10L, EntryUnit.ENTRIES).build()).buildConfig(Long.class, String.class);

    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().withCache("cache", cacheConfiguration)
        .build(true);

    Cache<Long, String> cache = cacheManager.getCache("cache", Long.class, String.class);
    for(long i = 0; i < 20; i++ ){
      cache.put(i, "Hello World");
    }
    assertThat(listener.evicted(), is(10));

    cache.clear();
    listener.resetEvictionCount();

    // tag::updateResourcesAtRuntime[]
    ResourcePools pools = ResourcePoolsBuilder.newResourcePoolsBuilder().heap(20L, EntryUnit.ENTRIES).build(); // <1>
    cache.getRuntimeConfiguration().updateResourcePools(pools); // <2>
    assertThat(cache.getRuntimeConfiguration().getResourcePools()
        .getPoolForResource(ResourceType.Core.HEAP).getSize(), is(20L));
    // end::updateResourcesAtRuntime[]
    
    for(long i = 0; i < 20; i++ ){
      cache.put(i, "Hello World");
    }
    assertThat(listener.evicted(), is(0));

    cacheManager.close();
  }

  @Test
  public void cacheCopiers() throws Exception {
    // tag::cacheCopiers[]
    CacheConfiguration<Description, Person> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES))
        .withKeyCopier((Copier) new DescriptionCopier()) // <1>
        .withValueCopier((Copier) new PersonCopier()) // <2>
        .buildConfig(Description.class, Person.class);

    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache("cache", cacheConfiguration)
        .build(true);

    Cache<Description, Person> cache = cacheManager.getCache("cache", Description.class, Person.class);

    Description desc = new Description(1234, "foo");
    Person person = new Person("Bar", 24);
    cache.put(desc, person);
    assertThat(cache.get(desc), equalTo(person));

    cacheManager.close();
    // end::cacheCopiers[]
  }

  @Test
  public void cacheSerializingCopiers() throws Exception {
    // tag::cacheSerializingCopiers[]
    CacheConfiguration<Long, Person> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES).build())
        .withValueSerializingCopier() // <1>
        .buildConfig(Long.class, Person.class);
    // end::cacheSerializingCopiers[]

    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache("cache", cacheConfiguration)
        .build(true);

    Cache<Long, Person> cache = cacheManager.getCache("cache", Long.class, Person.class);

    Description desc = new Description(1234, "foo");
    Person person = new Person("Bar", 24);
    cache.put(1L, person);
    assertThat(cache.get(1L), equalTo(person));
    assertThat(cache.get(1L), not(sameInstance(person)));

    cacheManager.close();
  }

  @Test
  public void defaultCopiers() throws Exception {
    // tag::defaultCopiers[]
    CacheConfiguration<Description, Person> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES).build())
        .buildConfig(Description.class, Person.class);

    CacheConfiguration<Long, Person> anotherCacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES).build())
        .buildConfig(Long.class, Person.class);

    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCopier(Description.class, DescriptionCopier.class) // <1>
        .withCopier(Person.class, PersonCopier.class)
        .withCache("cache", cacheConfiguration)   //<3>
        .withCache("anotherCache", anotherCacheConfiguration)   //<4>
        .build(true);

    Cache<Description, Person> cache = cacheManager.getCache("cache", Description.class, Person.class);
    Cache<Long, Person> anotherCache = cacheManager.getCache("anotherCache", Long.class, Person.class);

    Description desc = new Description(1234, "foo");
    Person person = new Person("Bar", 24);
    cache.put(desc, person);
    assertThat(cache.get(desc), equalTo(person));
    assertThat(cache.get(desc), is(not(sameInstance(person))));

    anotherCache.put(1L, person);
    assertThat(anotherCache.get(1L), equalTo(person));

    cacheManager.close();
    // end::defaultCopiers[]
  }

  @Test
  public void cacheServiceConfiguration() throws Exception {
    // tag::cacheServiceConfigurations[]
    CacheConfiguration<Description, Person> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES).build())
        .withKeyCopier((Class) DescriptionCopier.class) // <1>
        .withValueCopier((Copier) new PersonCopier()) // <2>
        .buildConfig(Description.class, Person.class);
    // end::cacheServiceConfigurations[]

    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache("cache", cacheConfiguration)
        .build(true);

    Cache<Description, Person> cache = cacheManager.getCache("cache", Description.class, Person.class);

    Description desc = new Description(1234, "foo");
    Person person = new Person("Bar", 24);
    cache.put(desc, person);
    assertThat(cache.get(desc), equalTo(person));

    cacheManager.close();
  }

  @Test
  public void cacheEvictionVeto() throws Exception {
    // tag::cacheEvictionVeto[]
    CacheConfiguration<Long, String> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder()
        .withEvictionVeto((EvictionVeto) new OddKeysEvictionVeto<Long, String>()) // <1>
        .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder()
            .heap(2L, EntryUnit.ENTRIES)) // <2>
        .buildConfig(Long.class, String.class);

    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache("cache", cacheConfiguration)
        .build(true);

    Cache<Long, String> cache = cacheManager.getCache("cache", Long.class, String.class);

    // Work with the cache
    cache.put(42L, "The Answer!");
    cache.put(41L, "The wrong Answer!");
    cache.put(39L, "The other wrong Answer!");

    cacheManager.close();
    // end::cacheEvictionVeto[]
  }


  private static class Description {
    int id;
    String alias;

    Description(Description other) {
      this.id = other.id;
      this.alias = other.alias;
    }

    Description(int id, String alias) {
      this.id = id;
      this.alias = alias;
    }

    @Override
    public boolean equals(final Object other) {
      if(this == other) return true;
      if(other == null || this.getClass() != other.getClass()) return false;

      Description that = (Description)other;
      if(id != that.id) return false;
      if ((alias == null) ? (alias != null) : !alias.equals(that.alias)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + id;
      result = 31 * result + (alias == null ? 0 : alias.hashCode());
      return result;
    }
  }

  private static class Person implements Serializable {
    String name;
    int age;

    Person(Person other) {
      this.name = other.name;
      this.age = other.age;
    }

    Person(String name, int age) {
      this.name = name;
      this.age = age;
    }

    @Override
    public boolean equals(final Object other) {
      if(this == other) return true;
      if(other == null || this.getClass() != other.getClass()) return false;

      Person that = (Person)other;
      if(age != that.age) return false;
      if((name == null) ? (that.name != null) : !name.equals(that.name)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + age;
      result = 31 * result + (name == null ? 0 : name.hashCode());
      return result;
    }
  }

  public static class DescriptionCopier extends ReadWriteCopier<Description> {

    @Override
    public Description copy(final Description obj) {
      return new Description(obj);
    }
  }

  public static class PersonCopier extends ReadWriteCopier<Person> {

    @Override
    public Person copy(final Person obj) {
      return new Person(obj);
    }
  }

  private String getStoragePath() throws URISyntaxException {
    return getClass().getClassLoader().getResource(".").toURI().getPath();
  }

}
