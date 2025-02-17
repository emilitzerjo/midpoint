/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.provisioning.impl.dummy;

import static com.evolveum.midpoint.provisioning.impl.ProvisioningTestUtil.getDefaultAccountObjectClass;
import static com.evolveum.midpoint.schema.GetOperationOptions.createNoFetchCollection;
import static com.evolveum.midpoint.schema.constants.SchemaConstants.RI_ACCOUNT_OBJECT_CLASS;

import static com.evolveum.midpoint.schema.util.ObjectQueryUtil.*;
import static com.evolveum.midpoint.test.DummyResourceContoller.*;

import static com.evolveum.midpoint.test.IntegrationTestTools.*;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.*;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.time.ZonedDateTime;
import java.util.*;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.provisioning.api.*;

import com.evolveum.midpoint.provisioning.impl.DummyTokenStorageImpl;

import com.evolveum.midpoint.schema.constants.MidPointConstants;

import com.evolveum.midpoint.schema.util.Resource;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import com.evolveum.icf.dummy.resource.*;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.*;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.provisioning.impl.ProvisioningTestUtil;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.internals.InternalCounters;
import com.evolveum.midpoint.schema.processor.*;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.test.ObjectChecker;
import com.evolveum.midpoint.test.ProvisioningScriptSpec;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * The test of Provisioning service on the API level. The test is using dummy
 * resource for speed and flexibility.
 *
 * @author Radovan Semancik
 */
@ContextConfiguration(locations = "classpath:ctx-provisioning-test-main.xml")
@DirtiesContext
@Listeners({ com.evolveum.midpoint.tools.testng.AlphabeticalMethodInterceptor.class })
public class TestDummy extends AbstractBasicDummyTest {

    private static final String BLACKBEARD_USERNAME = "BlackBeard";
    private static final String DRAKE_USERNAME = "Drake";
    // Make this ugly by design. it check for some caseExact/caseIgnore cases
    private static final String ACCOUNT_MURRAY_USERNAME = "muRRay";

    static final long VALID_FROM_MILLIS = 12322342345435L;
    static final long VALID_TO_MILLIS = 3454564324423L;

    private static final String GROUP_CORSAIRS_NAME = "corsairs";

    private String drakeAccountOid;

    private String morganIcfUid;
    private String williamIcfUid;
    String piratesIcfUid;
    private String pillageIcfUid;
    private String bargainIcfUid;
    private String leChuckIcfUid;
    private String blackbeardIcfUid;
    private String drakeIcfUid;
    private String corsairsIcfUid;
    private String corsairsShadowOid;
    private String meathookAccountOid;

    protected String getMurrayRepoIcfName() {
        return ACCOUNT_MURRAY_USERNAME;
    }

    protected String getBlackbeardRepoIcfName() {
        return BLACKBEARD_USERNAME;
    }

    protected String getDrakeRepoIcfName() {
        return DRAKE_USERNAME;
    }

    protected ItemComparisonResult getExpectedPasswordComparisonResultMatch() {
        return ItemComparisonResult.NOT_APPLICABLE;
    }

    protected ItemComparisonResult getExpectedPasswordComparisonResultMismatch() {
        return ItemComparisonResult.NOT_APPLICABLE;
    }

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);
//        InternalMonitor.setTraceConnectorOperation(true);
    }

    // test000-test100 in the superclasses

    @Test
    public void test101AddAccountWithoutName() throws Exception {
        // GIVEN
        Task syncTask = getTestTask();
        OperationResult result = syncTask.getResult();
        syncServiceMock.reset();

        ShadowType account = parseObjectType(ACCOUNT_MORGAN_FILE, ShadowType.class);

        display("Adding shadow", account.asPrismObject());

        // WHEN
        when("add");
        String addedObjectOid = provisioningService.addObject(account.asPrismObject(), null, null, syncTask, result);

        // THEN
        then("add");
        result.computeStatus();
        display("add object result", result);
        TestUtil.assertSuccess("addObject has failed (result)", result);
        assertEquals(ACCOUNT_MORGAN_OID, addedObjectOid);

        ShadowType accountType = getShadowRepo(ACCOUNT_MORGAN_OID).asObjectable();
        PrismAsserts.assertEqualsPolyString("Account name was not generated (repository)", ACCOUNT_MORGAN_NAME, accountType.getName());
        morganIcfUid = getIcfUid(accountType);

        syncServiceMock.assertSingleNotifySuccessOnly();

        // WHEN
        when("get");
        PrismObject<ShadowType> provisioningAccount = provisioningService.getObject(ShadowType.class,
                ACCOUNT_MORGAN_OID, null, syncTask, result);

        // THEN
        then("get");
        display("account from provisioning", provisioningAccount);
        ShadowType provisioningAccountType = provisioningAccount.asObjectable();
        PrismAsserts.assertEqualsPolyString("Account name was not generated (provisioning)", transformNameFromResource(ACCOUNT_MORGAN_NAME),
                provisioningAccountType.getName());
        // MID-4754
        assertAttribute(provisioningAccount,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_ENLIST_TIMESTAMP_NAME,
                XmlTypeConverter.createXMLGregorianCalendar(ZonedDateTime.parse(ACCOUNT_MORGAN_PASSWORD_ENLIST_TIMESTAMP)));

        assertNull("The _PASSWORD_ attribute sneaked into shadow", ShadowUtil.getAttributeValues(
                provisioningAccountType, new QName(SchemaConstants.NS_ICF_SCHEMA, "password")));

        // Check if the account was created in the dummy resource
        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_MORGAN_NAME), getIcfUid(provisioningAccountType));
        displayDumpable("Dummy account", dummyAccount);
        assertNotNull("No dummy account", dummyAccount);
        assertEquals("Fullname is wrong", "Captain Morgan", dummyAccount.getAttributeValue("fullname"));
        assertTrue("The account is not enabled", dummyAccount.isEnabled());
        assertEquals("Wrong password", ACCOUNT_MORGAN_PASSWORD, dummyAccount.getPassword());
        // MID-4754
        ZonedDateTime enlistTimestamp = dummyAccount.getAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_ENLIST_TIMESTAMP_NAME, ZonedDateTime.class);
        assertNotNull("No enlistTimestamp in dummy account", enlistTimestamp);
        assertEqualTime("Wrong enlistTimestamp in dummy account", ACCOUNT_MORGAN_PASSWORD_ENLIST_TIMESTAMP, enlistTimestamp);

        // Check if the shadow is in the repo
        PrismObject<ShadowType> shadowFromRepo = getShadowRepo(addedObjectOid);
        assertNotNull("Shadow was not created in the repository", shadowFromRepo);
        displayValue("Repository shadow", shadowFromRepo.debugDump());

        checkRepoAccountShadow(shadowFromRepo);

        PrismSerializer<String> serializer = prismContext.xmlSerializer().options(SerializationOptions.createSerializeForExport());

        serializer.serialize(provisioningAccount);
        serializer.serialize(shadowFromRepo);

        //prismContext.xnodeSerializer().serialize(item)

        // MID-4397
        assertRepoShadowCredentials(shadowFromRepo, ACCOUNT_MORGAN_PASSWORD);

        checkUniqueness(account.asPrismObject());

        assertSteadyResource();
    }

    // test102-test106 in the superclasses

    /**
     * Make a native modification to an account and read it with max staleness option.
     * As there is no caching enabled this should throw an error.
     * <p>
     * Note: This test is overridden in TestDummyCaching
     * <p>
     * MID-3481
     */
    @Test
    public void test107AGetModifiedAccountFromCacheMax() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        DummyAccount accountWill = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        accountWill.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Nice Pirate");
        accountWill.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Interceptor");
        accountWill.setEnabled(true);

        Collection<SelectorOptions<GetOperationOptions>> options =
                SelectorOptions.createCollection(GetOperationOptions.createMaxStaleness());

        XMLGregorianCalendar startTs = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        when();

        try {
            provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, options, task, result);

            assertNotReached();
        } catch (ConfigurationException e) {
            then();
            displayExpectedException(e);
            assertFailure(result);
        }

        PrismObject<ShadowType> shadowRepo = getShadowRepo(ACCOUNT_WILL_OID);
        checkRepoAccountShadowWillBasic(shadowRepo, null, startTs, null);

        assertRepoShadowCachedAttributeValue(shadowRepo, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Pirate");
        assertRepoShadowCachedAttributeValue(shadowRepo, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Black Pearl");
        assertRepoShadowCachedAttributeValue(shadowRepo, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME, "Sword", "LOVE");
        assertRepoShadowCachedAttributeValue(shadowRepo, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOOT_NAME, 42);
        assertRepoShadowCacheActivation(shadowRepo, ActivationStatusType.DISABLED);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 0);

        assertSteadyResource();
    }

    /**
     * Make a native modification to an account and read it with high staleness option.
     * In this test there is no caching enabled, so this should return fresh data.
     * <p>
     * Note: This test is overridden in TestDummyCaching
     * <p>
     * MID-3481
     */
    @Test
    public void test107BGetModifiedAccountFromCacheHighStaleness() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        DummyAccount accountWill = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        accountWill.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Very Nice Pirate");
        accountWill.setEnabled(true);

        Collection<SelectorOptions<GetOperationOptions>> options =
                SelectorOptions.createCollection(GetOperationOptions.createStaleness(1000000L));

        XMLGregorianCalendar startTs = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        when();

        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, options, task, result);

        // THEN
        then();
        result.computeStatus();
        display("getObject result", result);
        TestUtil.assertSuccess(result);

        assertAttribute(shadow, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Very Nice Pirate");

        PrismObject<ShadowType> shadowRepo = getShadowRepo(ACCOUNT_WILL_OID);
        checkRepoAccountShadowWillBasic(shadowRepo, null, startTs, null);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1);

        assertSteadyResource();
    }

    /**
     * Incomplete attributes should not be cached.
     */
    @Test
    public void test107CSkipCachingForIncompleteAttributes() throws Exception {
        // overridden in TestDummyCaching
    }

    /**
     * Staleness of one millisecond is too small for the cache to work.
     * Fresh data should be returned - both in case the cache is enabled and disabled.
     * MID-3481
     */
    @Test
    public void test108GetAccountLowStaleness() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        Collection<SelectorOptions<GetOperationOptions>> options =
                SelectorOptions.createCollection(GetOperationOptions.createStaleness(1L));

        XMLGregorianCalendar startTs = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        when();
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, options, task, result);

        // THEN
        then();
        result.computeStatus();
        display("getObject result", result);
        TestUtil.assertSuccess(result);
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1);

        XMLGregorianCalendar endTs = clock.currentTimeXMLGregorianCalendar();

        display("Retrieved account shadow", shadow);

        assertNotNull("No dummy account", shadow);

        checkAccountShadow(shadow, result, true);
        assertAttribute(shadow, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Very Nice Pirate");
        assertAttribute(shadow, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Interceptor");
        assertAttribute(shadow, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME, "Sword", "LOVE");
        assertAttribute(shadow, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOOT_NAME, 42);
        Collection<ResourceAttribute<?>> attributes = ShadowUtil.getAttributes(shadow);
        assertEquals("Unexpected number of attributes", 7, attributes.size());

        PrismObject<ShadowType> shadowRepo = getShadowRepo(ACCOUNT_WILL_OID);
        checkRepoAccountShadowWillBasic(shadowRepo, startTs, endTs, null);

        assertRepoShadowCachedAttributeValue(shadowRepo, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Very Nice Pirate");
        assertRepoShadowCachedAttributeValue(shadowRepo, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Interceptor");
        assertRepoShadowCachedAttributeValue(shadowRepo, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME, "sword", "love");
        assertRepoShadowCachedAttributeValue(shadowRepo, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOOT_NAME, 42);

        checkUniqueness(shadow);

        assertCachingMetadata(shadow, false, startTs, endTs);

        assertSteadyResource();
    }

    /**
     * Clean up after caching tests so we won't break subsequent tests.
     * MID-3481
     */
    @Test
    public void test109ModifiedAccountCleanup() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        DummyAccount accountWill = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        // Modify this back so won't break subsequent tests
        accountWill.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Flying Dutchman");
        accountWill.replaceAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME);
        accountWill.setEnabled(true);

        XMLGregorianCalendar startTs = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);

        // THEN
        assertSuccess(result);
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1);

        XMLGregorianCalendar endTs = clock.currentTimeXMLGregorianCalendar();

        display("Retrieved account shadow", shadow);

        assertNotNull("No dummy account", shadow);

        checkAccountWill(shadow, result, startTs, endTs);
        PrismObject<ShadowType> shadowRepo = getShadowRepo(ACCOUNT_WILL_OID);
        checkRepoAccountShadowWill(shadowRepo, startTs, endTs);

        checkUniqueness(shadow);

        assertCachingMetadata(shadow, false, startTs, endTs);

        assertSteadyResource();
    }

    @Test
    public void test110SearchIterative() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        // Make sure there is an account on resource that the provisioning has
        // never seen before, so there is no shadow
        // for it yet.
        DummyAccount newAccount = new DummyAccount("meathook");
        newAccount.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Meathook");
        newAccount.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Sea Monkey");
        newAccount.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME, "hook");
        newAccount.addAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOOT_NAME, 666);
        newAccount.setEnabled(true);
        newAccount.setPassword("parrotMonster");
        dummyResource.addAccount(newAccount);

        ObjectQuery query = createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID, RI_ACCOUNT_OBJECT_CLASS);

        final XMLGregorianCalendar startTs = clock.currentTimeXMLGregorianCalendar();

        final Holder<Boolean> seenMeathookHolder = new Holder<>(false);
        final List<PrismObject<ShadowType>> foundObjects = new ArrayList<>();
        ResultHandler<ShadowType> handler = (object, parentResult) -> {
            foundObjects.add(object);
            display("Found", object);

            XMLGregorianCalendar endTs = clock.currentTimeXMLGregorianCalendar();

            assertTrue(object.canRepresent(ShadowType.class));
            try {
                checkAccountShadow(object, parentResult, true);
            } catch (ConfigurationException | SchemaException e) {
                throw new SystemException(e.getMessage(), e);
            }

            assertCachingMetadata(object, false, startTs, endTs);

            if (object.asObjectable().getName().getOrig().equals("meathook")) {
                meathookAccountOid = object.getOid();
                seenMeathookHolder.setValue(true);
                try {
                    Integer loot = ShadowUtil.getAttributeValue(object, dummyResourceCtl.getAttributeQName(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOOT_NAME));
                    assertNotNull(loot);
                    assertEquals("Wrong meathook's loot", 666, (int) loot);
                } catch (SchemaException e) {
                    throw new SystemException(e.getMessage(), e);
                }
            }

            return true;
        };
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        provisioningService.searchObjectsIterative(ShadowType.class, query, null, handler, task, result);

        // THEN

        XMLGregorianCalendar endTs = clock.currentTimeXMLGregorianCalendar();
        assertSuccess(result);
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1);

        assertEquals(4, foundObjects.size());
        checkUniqueness(foundObjects);
        assertProtected(foundObjects, 1);

        PrismObject<ShadowType> shadowWillRepo = getShadowRepo(ACCOUNT_WILL_OID);
        assertRepoShadowCachedAttributeValue(shadowWillRepo,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Flying Dutchman");
        checkRepoAccountShadowWill(shadowWillRepo, startTs, endTs);

        PrismObject<ShadowType> shadowMeathook = getShadowRepo(meathookAccountOid);
        display("Meathook shadow", shadowMeathook);
        assertRepoShadowCachedAttributeValue(shadowMeathook,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME, "hook");
        assertRepoCachingMetadata(shadowMeathook, startTs, endTs);

        // And again ...

        foundObjects.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        XMLGregorianCalendar startTs2 = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        provisioningService.searchObjectsIterative(ShadowType.class, query, null, handler, task, result);

        // THEN

        XMLGregorianCalendar endTs2 = clock.currentTimeXMLGregorianCalendar();
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1);

        display("Found shadows", foundObjects);

        assertEquals(4, foundObjects.size());
        checkUniqueness(foundObjects);
        assertProtected(foundObjects, 1);

        PrismObject<ShadowType> shadowWillRepo2 = getShadowRepo(ACCOUNT_WILL_OID);
        checkRepoAccountShadowWill(shadowWillRepo2, startTs2, endTs2);

        PrismObject<ShadowType> shadowMeathook2 = getShadowRepo(meathookAccountOid);
        assertRepoShadowCachedAttributeValue(shadowMeathook2,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME, "hook");
        assertRepoCachingMetadata(shadowMeathook2, startTs2, endTs2);

        assertSteadyResource();
    }

    @Test
    public void test111SearchIterativeNoFetch() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        ObjectQuery query = createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID, RI_ACCOUNT_OBJECT_CLASS);

        final XMLGregorianCalendar startTs = clock.currentTimeXMLGregorianCalendar();

        final List<PrismObject<ShadowType>> foundObjects = new ArrayList<>();
        ResultHandler<ShadowType> handler = (shadow, parentResult) -> {
            foundObjects.add(shadow);

            assertTrue(shadow.canRepresent(ShadowType.class));
            try {
                checkCachedAccountShadow(shadow, parentResult, false, null, startTs);
            } catch (ConfigurationException | SchemaException e) {
                throw new SystemException(e.getMessage(), e);
            }

            assertRepoCachingMetadata(shadow, null, startTs);

            if (shadow.asObjectable().getName().getOrig().equals("meathook")) {
                assertRepoShadowCachedAttributeValue(shadow, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Sea Monkey");
            }

            return true;
        };

        Collection<SelectorOptions<GetOperationOptions>> options =
                SelectorOptions.createCollection(GetOperationOptions.createNoFetch());

        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        provisioningService.searchObjectsIterative(ShadowType.class, query, options, handler, task, result);

        // THEN
        assertSuccess(result);
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 0);

        display("Found shadows", foundObjects);

        assertEquals(4, foundObjects.size());
        checkUniqueness(foundObjects);
        assertProtected(foundObjects, 1); // MID-1640

        assertSteadyResource();
    }

    /**
     * Raw search should not go to the resource as well.
     */
    @Test
    public void test111bSearchIterativeRaw() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        ObjectQuery query = createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID, RI_ACCOUNT_OBJECT_CLASS);

        final List<PrismObject<ShadowType>> foundObjects = new ArrayList<>();
        ResultHandler<ShadowType> handler = (shadow, parentResult) -> {
            foundObjects.add(shadow);
            assertTrue(shadow.canRepresent(ShadowType.class));
            return true;
        };

        Collection<SelectorOptions<GetOperationOptions>> options = GetOperationOptions.createRawCollection();

        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        when();
        provisioningService.searchObjectsIterative(ShadowType.class, query, options, handler, task, result);

        then();
        assertSuccess(result);
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 0);

        display("Found shadows", foundObjects);

        assertEquals(4, foundObjects.size());
        checkUniqueness(foundObjects);
        // In raw mode there should be no protected shadow info. But currently this is not implemented fully: MID-7419
        //assertProtected(foundObjects, 0);

        assertSteadyResource();
    }

    @Test
    public void test112SearchIterativeKindIntent() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        ObjectQuery query = createResourceAndKindIntent(RESOURCE_DUMMY_OID, ShadowKindType.ACCOUNT, "default");
        displayDumpable("query", query);

        List<PrismObject<ShadowType>> foundObjects = new ArrayList<>();

        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        provisioningService.searchObjectsIterative(ShadowType.class, query, null,
                (object, parentResult) -> {
                    foundObjects.add(object);
                    return true;
                },
                task, result);

        // THEN
        result.computeStatus();
        display("searchObjectsIterative result", result);
        TestUtil.assertSuccess(result);
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1);

        display("Found shadows", foundObjects);

        assertEquals(4, foundObjects.size());
        checkUniqueness(foundObjects);
        assertProtected(foundObjects, 1); // MID-1640

        assertSteadyResource();
    }

    @SuppressWarnings("SameParameterValue")
    protected <T extends ShadowType> void assertProtected(List<PrismObject<T>> shadows, int expectedNumberOfProtectedShadows) {
        int actual = countProtected(shadows);
        assertEquals("Unexpected number of protected shadows", expectedNumberOfProtectedShadows, actual);
    }

    private <T extends ShadowType> int countProtected(List<PrismObject<T>> shadows) {
        return (int) shadows.stream()
                .filter(shadow -> Boolean.TRUE.equals(shadow.asObjectable().isProtectedObject()))
                .count();
    }

    @Test
    public void test113SearchAllShadowsInRepository() throws Exception {
        // GIVEN
        OperationResult result = createOperationResult();
        ObjectQuery query = IntegrationTestTools.createAllShadowsQuery(resourceBean, prismContext);
        displayDumpable("All shadows query", query);

        when();
        List<PrismObject<ShadowType>> allShadows = repositoryService.searchObjects(ShadowType.class,
                query, null, result);

        then();
        assertSuccess(result);

        display("Found " + allShadows.size() + " shadows");
        display("Found shadows", allShadows);

        assertFalse("No shadows found", allShadows.isEmpty());
        assertEquals("Wrong number of results", 4, allShadows.size());

        assertSteadyResource();
    }

    @Test
    public void test114SearchAllAccounts() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = createOperationResult();
        ObjectQuery query = IntegrationTestTools.createAllShadowsQuery(resourceBean,
                SchemaConstants.ACCOUNT_OBJECT_CLASS_LOCAL_NAME, prismContext);
        displayDumpable("All shadows query", query);

        when();
        List<PrismObject<ShadowType>> allShadows = provisioningService.searchObjects(ShadowType.class,
                query, null, task, result);

        then();
        assertSuccess(result);

        display("Found " + allShadows.size() + " shadows");

        assertFalse("No shadows found", allShadows.isEmpty());
        assertEquals("Wrong number of results", 4, allShadows.size());

        checkUniqueness(allShadows);
        assertProtected(allShadows, 1);

        assertSteadyResource();
    }

    @Test
    public void test115CountAllAccounts() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = createOperationResult();
        ObjectQuery query = IntegrationTestTools.createAllShadowsQuery(resourceBean,
                SchemaConstants.ACCOUNT_OBJECT_CLASS_LOCAL_NAME, prismContext);
        displayDumpable("All shadows query", query);

        when();
        Integer count = provisioningService.countObjects(ShadowType.class, query, null, task, result);

        then();
        assertSuccess(result);

        display("Found " + count + " shadows");

        assertEquals("Wrong number of count results", getTest115ExpectedCount(), count);

        assertSteadyResource();
    }

    protected Integer getTest115ExpectedCount() {
        return 4;
    }

    @Test
    public void test116SearchNullQueryResource() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        when();
        List<PrismObject<ResourceType>> allResources = provisioningService.searchObjects(ResourceType.class,
                null, null, task, result);

        then();
        assertSuccess(result);

        display("Found " + allResources.size() + " resources");

        assertFalse("No resources found", allResources.isEmpty());
        assertEquals("Wrong number of results", 1, allResources.size());

        assertSteadyResource();
    }

    @Test
    public void test117CountNullQueryResource() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        when();
        int count = provisioningService.countObjects(ResourceType.class, null, null, task, result);

        then();
        assertSuccess(result);

        display("Counted " + count + " resources");

        assertEquals("Wrong count", 1, count);

        assertSteadyResource();
    }

    /**
     * Search for all accounts with long staleness option. This is a search,
     * so we cannot evaluate whether our data are fresh enough. Therefore
     * search on resource will always be performed.
     * MID-3481
     */
    @Test
    public void test118SearchAllAccountsLongStaleness() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = createOperationResult();
        ObjectQuery query = IntegrationTestTools.createAllShadowsQuery(resourceBean,
                SchemaConstants.ACCOUNT_OBJECT_CLASS_LOCAL_NAME, prismContext);
        displayDumpable("All shadows query", query);

        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        Collection<SelectorOptions<GetOperationOptions>> options =
                SelectorOptions.createCollection(GetOperationOptions.createStaleness(1000000L));

        when();
        List<PrismObject<ShadowType>> allShadows = provisioningService.searchObjects(ShadowType.class,
                query, options, task, result);

        then();
        assertSuccess(result);

        display("Found " + allShadows.size() + " shadows");

        assertFalse("No shadows found", allShadows.isEmpty());
        assertEquals("Wrong number of results", 4, allShadows.size());

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1);

        checkUniqueness(allShadows);
        assertProtected(allShadows, 1);

        assertSteadyResource();
    }

    /**
     * Search for all accounts with maximum staleness option.
     * This is supposed to return only cached data. Therefore
     * repo search is performed. But as caching is
     * not enabled in this test only errors will be returned.
     * <p>
     * Note: This test is overridden in TestDummyCaching
     * <p>
     * MID-3481
     */
    @Test
    public void test119SearchAllAccountsMaxStaleness() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = createOperationResult();
        ObjectQuery query = IntegrationTestTools.createAllShadowsQuery(resourceBean,
                SchemaConstants.ACCOUNT_OBJECT_CLASS_LOCAL_NAME, prismContext);
        displayDumpable("All shadows query", query);

        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        Collection<SelectorOptions<GetOperationOptions>> options =
                SelectorOptions.createCollection(GetOperationOptions.createMaxStaleness());

        when();
        List<PrismObject<ShadowType>> allShadows = provisioningService.searchObjects(ShadowType.class,
                query, options, task, result);

        then();
        assertFailure(result);

        display("Found " + allShadows.size() + " shadows");

        assertFalse("No shadows found", allShadows.isEmpty());
        assertEquals("Wrong number of results", 4, allShadows.size());

        for (PrismObject<ShadowType> shadow : allShadows) {
            display("Found shadow (error expected)", shadow);
            OperationResultType fetchResult = shadow.asObjectable().getFetchResult();
            assertNotNull("No fetch result status in " + shadow, fetchResult);
            assertEquals("Wrong fetch result status in " + shadow, OperationResultStatusType.FATAL_ERROR, fetchResult.getStatus());
        }

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 0);

        assertProtected(allShadows, 1);

        assertSteadyResource();
    }

    @Test
    public void test120ModifyWillReplaceFullname() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
                ACCOUNT_WILL_OID, dummyResourceCtl.getAttributeFullnamePath(), "Pirate Will Turner");
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        then();
        assertSuccess(result);

        delta.checkConsistence();
        //noinspection unchecked
        assertDummyAccount(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid)
                .assertAttribute(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Pirate Will Turner");

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test121ModifyObjectAddPirate() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationAddProperty(
                ShadowType.class, ACCOUNT_WILL_OID, DUMMY_ACCOUNT_ATTRIBUTE_TITLE_PATH, "Pirate");
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        then();
        assertSuccess(result);

        delta.checkConsistence();
        // check if attribute was changed
        assertDummyAccountAttributeValues(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid,
                DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Pirate");

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test122ModifyObjectAddCaptain() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationAddProperty(
                ShadowType.class, ACCOUNT_WILL_OID, DUMMY_ACCOUNT_ATTRIBUTE_TITLE_PATH, "Captain");
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        then();
        assertSuccess(result);

        delta.checkConsistence();
        // check if attribute was changed
        assertDummyAccountAttributeValues(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid,
                DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Pirate", "Captain");

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test123ModifyObjectDeletePirate() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationDeleteProperty(
                ShadowType.class, ACCOUNT_WILL_OID, DUMMY_ACCOUNT_ATTRIBUTE_TITLE_PATH, "Pirate");
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        then();
        assertSuccess(result);

        delta.checkConsistence();
        // check if attribute was changed
        assertDummyAccountAttributeValues(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid,
                DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Captain");

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    /**
     * Try to add the same value that the account attribute already has. Resources that do not tolerate this will fail
     * unless the mechanism to compensate for this works properly.
     */
    @Test
    public void test124ModifyAccountWillAddCaptainAgain() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationAddProperty(
                ShadowType.class, ACCOUNT_WILL_OID, DUMMY_ACCOUNT_ATTRIBUTE_TITLE_PATH, "Captain");
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        then();
        assertSuccess(result);

        delta.checkConsistence();
        // check if attribute was changed
        assertDummyAccountAttributeValues(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid,
                DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, "Captain");

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    /**
     * MID-4397
     */
    @Test
    public void test125CompareAccountWillPassword() throws Exception {
        testComparePassword("match", ACCOUNT_WILL_OID, accountWillCurrentPassword, getExpectedPasswordComparisonResultMatch());
        testComparePassword("mismatch", ACCOUNT_WILL_OID, "I woulD NeVeR ever USE this PASSword", getExpectedPasswordComparisonResultMismatch());

        assertSteadyResource();
    }

    /**
     * MID-4397
     */
    @Test
    public void test126ModifyAccountWillPassword() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createAccountPasswordDelta(ACCOUNT_WILL_OID, ACCOUNT_WILL_PASSWORD_123, null);
        displayDumpable("ObjectDelta", delta);

        // WHEN
        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        then();
        assertSuccess(result);

        assertDummyAccount(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid)
                .assertPassword(ACCOUNT_WILL_PASSWORD_123)
                .assertLastModifier(null);

        accountWillCurrentPassword = ACCOUNT_WILL_PASSWORD_123;

        // Check if the shadow is in the repo
        PrismObject<ShadowType> repoShadow = getShadowRepo(ACCOUNT_WILL_OID);
        assertNotNull("Shadow was not created in the repository", repoShadow);
        display("Repository shadow", repoShadow);

        checkRepoAccountShadow(repoShadow);
        assertRepoShadowCredentials(repoShadow, ACCOUNT_WILL_PASSWORD_123);

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    /**
     * MID-4397
     */
    @Test
    public void test127CompareAccountWillPassword() throws Exception {
        testComparePassword("match", ACCOUNT_WILL_OID, accountWillCurrentPassword, getExpectedPasswordComparisonResultMatch());
        testComparePassword("mismatch old password", ACCOUNT_WILL_OID, ACCOUNT_WILL_PASSWORD, getExpectedPasswordComparisonResultMismatch());
        testComparePassword("mismatch", ACCOUNT_WILL_OID, "I woulD NeVeR ever USE this PASSword", getExpectedPasswordComparisonResultMismatch());

        assertSteadyResource();
    }

    @SuppressWarnings("SameParameterValue")
    protected void testComparePassword(String label, String shadowOid,
            String expectedPassword, ItemComparisonResult expectedResult) throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        // WHEN (match)
        when();
        ItemComparisonResult comparisonResult = provisioningService.compare(ShadowType.class, shadowOid, SchemaConstants.PATH_PASSWORD_VALUE,
                expectedPassword, task, result);

        // THEN (match)
        then();
        assertSuccess(result);

        displayValue("Comparison result (" + label + ")", comparisonResult);
        assertEquals("Wrong comparison result (" + label + ")", expectedResult, comparisonResult);

        syncServiceMock.assertNoNotifications();
    }

    /**
     * Set a null value to the (native) dummy attribute. The UCF layer should filter that out.
     */
    @Test
    public void test129NullAttributeValue() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        DummyAccount willDummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        willDummyAccount.replaceAttributeValue(DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME, null);

        when();
        PrismObject<ShadowType> accountWill = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);

        then();
        assertSuccess(result);

        ResourceAttributeContainer attributesContainer = ShadowUtil.getAttributesContainer(accountWill);
        ResourceAttribute<Object> titleAttribute = attributesContainer.findAttribute(new QName(MidPointConstants.NS_RI, DUMMY_ACCOUNT_ATTRIBUTE_TITLE_NAME));
        assertNull("Title attribute sneaked in", titleAttribute);

        accountWill.checkConsistence();

        assertSteadyResource();
    }

    @Test
    public void test131AddScript() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();
        dummyResource.purgeScriptHistory();

        ShadowType account = parseObjectType(ACCOUNT_SCRIPT_FILE, ShadowType.class);
        display("Account before add", account);

        OperationProvisioningScriptsType scriptsType = unmarshalValueFromFile(SCRIPTS_FILE);
        displayValue("Provisioning scripts", PrismTestUtil.serializeAnyDataWrapped(scriptsType));

        when();
        String addedObjectOid = provisioningService.addObject(account.asPrismObject(), scriptsType, null, task, result);

        then();
        assertSuccess("addObject has failed (result)", result);
        assertEquals(ACCOUNT_NEW_SCRIPT_OID, addedObjectOid);

        ShadowType repoShadow = getShadowRepo(ACCOUNT_NEW_SCRIPT_OID).asObjectable();
        assertShadowName(repoShadow, "william");

        syncServiceMock.assertSingleNotifySuccessOnly();

        ShadowType provisioningShadow = provisioningService.getObject(ShadowType.class,
                ACCOUNT_NEW_SCRIPT_OID, null, task, result).asObjectable();
        PrismAsserts.assertEqualsPolyString("Wrong name", transformNameFromResource("william"), provisioningShadow.getName());
        williamIcfUid = getIcfUid(repoShadow);

        // Check if the account was created in the dummy resource

        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource("william"), williamIcfUid);
        assertNotNull("No dummy account", dummyAccount);
        assertEquals("Fullname is wrong", "William Turner", dummyAccount.getAttributeValue("fullname"));
        assertTrue("The account is not enabled", dummyAccount.isEnabled());
        assertEquals("Wrong password", "3lizab3th123", dummyAccount.getPassword());

        ProvisioningScriptSpec beforeScript = new ProvisioningScriptSpec("In the beginning ...");
        beforeScript.addArgSingle("HOMEDIR", "jbond");
        ProvisioningScriptSpec afterScript = new ProvisioningScriptSpec("Hello World");
        afterScript.addArgSingle("which", "this");
        afterScript.addArgSingle("when", "now");
        IntegrationTestTools.assertScripts(dummyResource.getScriptHistory(), beforeScript, afterScript);

        assertSteadyResource();
    }

    // MID-1113
    @Test
    public void test132ModifyScript() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();
        dummyResource.purgeScriptHistory();

        OperationProvisioningScriptsType scripts = unmarshalValueFromFile(SCRIPTS_FILE);
        displayValue("Provisioning scripts", PrismTestUtil.serializeAnyDataWrapped(scripts));

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
                ACCOUNT_NEW_SCRIPT_OID, dummyResourceCtl.getAttributeFullnamePath(), "Will Turner");
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, ACCOUNT_NEW_SCRIPT_OID, delta.getModifications(),
                scripts, null, task, result);

        then();
        assertSuccess(result);
        syncServiceMock.assertSingleNotifySuccessOnly();

        // Check if the account was modified in the dummy resource

        DummyAccount dummyAccount = getDummyAccountAssert("william", williamIcfUid);
        assertNotNull("No dummy account", dummyAccount);
        assertEquals("Fullname is wrong", "Will Turner", dummyAccount.getAttributeValue("fullname"));
        assertTrue("The account is not enabled", dummyAccount.isEnabled());
        assertEquals("Wrong password", "3lizab3th123", dummyAccount.getPassword());

        ProvisioningScriptSpec beforeScript = new ProvisioningScriptSpec("Where am I?");
        ProvisioningScriptSpec afterScript = new ProvisioningScriptSpec("Still here");
        afterScript.addArgMulti("status", "dead", "alive");
        IntegrationTestTools.assertScripts(dummyResource.getScriptHistory(), beforeScript, afterScript);

        assertSteadyResource();
    }

    /**
     * This test modifies account shadow property that does NOT result in account modification
     * on resource. The scripts must not be executed.
     */
    @Test
    public void test133ModifyScriptNoExec() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();
        dummyResource.purgeScriptHistory();

        OperationProvisioningScriptsType scripts = unmarshalValueFromFile(SCRIPTS_FILE);
        displayValue("Provisioning scripts", PrismTestUtil.serializeAnyDataWrapped(scripts));

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
                ACCOUNT_NEW_SCRIPT_OID, ShadowType.F_DESCRIPTION, "Blah blah");
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, ACCOUNT_NEW_SCRIPT_OID, delta.getModifications(),
                scripts, null, task, result);

        then();
        assertSuccess("modifyObject has failed (result)", result);
        syncServiceMock.assertSingleNotifySuccessOnly();

        // Check if the account was modified in the dummy resource

        DummyAccount dummyAccount = getDummyAccountAssert("william", williamIcfUid);
        assertNotNull("No dummy account", dummyAccount);
        assertEquals("Fullname is wrong", "Will Turner", dummyAccount.getAttributeValue("fullname"));
        assertTrue("The account is not enabled", dummyAccount.isEnabled());
        assertEquals("Wrong password", "3lizab3th123", dummyAccount.getPassword());

        IntegrationTestTools.assertScripts(dummyResource.getScriptHistory());

        assertSteadyResource();
    }

    @Test
    public void test134DeleteScript() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();
        dummyResource.purgeScriptHistory();

        OperationProvisioningScriptsType scriptsType = unmarshalValueFromFile(SCRIPTS_FILE);
        displayValue("Provisioning scripts", PrismTestUtil.serializeAnyDataWrapped(scriptsType));

        when();
        provisioningService.deleteObject(ShadowType.class, ACCOUNT_NEW_SCRIPT_OID, null, scriptsType,
                task, result);

        then();
        assertSuccess("modifyObject has failed (result)", result);
        syncServiceMock.assertSingleNotifySuccessOnly();

        // Check if the account was modified in the dummy resource

        DummyAccount dummyAccount = getDummyAccount("william", williamIcfUid);
        assertNull("Dummy account not gone", dummyAccount);

        ProvisioningScriptSpec beforeScript = new ProvisioningScriptSpec("Goodbye World");
        beforeScript.addArgMulti("what", "cruel");
        ProvisioningScriptSpec afterScript = new ProvisioningScriptSpec("R.I.P.");
        IntegrationTestTools.assertScripts(dummyResource.getScriptHistory(), beforeScript, afterScript);

        assertSteadyResource();
    }

    @Test
    public void test135ExecuteScript() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();
        dummyResource.purgeScriptHistory();

        OperationProvisioningScriptsType scripts = unmarshalValueFromFile(SCRIPTS_FILE);
        displayValue("Provisioning scripts", PrismTestUtil.serializeAnyDataWrapped(scripts));

        ProvisioningScriptType script = scripts.getScript().get(0);

        when();
        provisioningService.executeScript(RESOURCE_DUMMY_OID, script, task, result);

        then();
        assertSuccess("executeScript has failed (result)", result);

        ProvisioningScriptSpec expectedScript = new ProvisioningScriptSpec("Where to go now?");
        expectedScript.addArgMulti("direction", "left", "right");
        IntegrationTestTools.assertScripts(dummyResource.getScriptHistory(), expectedScript);

        assertSteadyResource();
    }

    @Test
    public void test150DisableAccount() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ShadowType accountType = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task,
                result).asObjectable();
        assertNotNull(accountType);

        display("Retrieved account shadow", accountType);

        DummyAccount dummyAccountBefore = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertTrue(dummyAccountBefore.isEnabled());

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
                ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS,
                ActivationStatusType.DISABLED);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(),
                delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);

        delta.checkConsistence();
        // check if activation was changed
        DummyAccount dummyAccountAfter = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertFalse("Dummy account " + transformNameFromResource(ACCOUNT_WILL_USERNAME) + " is enabled, expected disabled", dummyAccountAfter.isEnabled());

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test151SearchDisabledAccounts() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectQuery query = createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID, getDefaultAccountObjectClass(resourceBean));
        filterAnd(query.getFilter(),
                prismContext.queryFor(ShadowType.class)
                        .item(ShadowType.F_ACTIVATION, ActivationType.F_ADMINISTRATIVE_STATUS).eq(ActivationStatusType.DISABLED)
                        .buildFilter(), prismContext);

        syncServiceMock.reset();

        when();
        SearchResultList<PrismObject<ShadowType>> resultList = provisioningService.searchObjects(ShadowType.class, query, null, task, result);

        then();
        result.computeStatus();
        display(result);
        TestUtil.assertSuccess(result);

        assertEquals("Unexpected number of search results", 1, resultList.size());
        PrismObject<ShadowType> shadow = resultList.get(0);
        display("Shadow", shadow);
        assertActivationAdministrativeStatus(shadow, ActivationStatusType.DISABLED);

        assertSteadyResource();
    }

    @Test
    public void test152ActivationStatusUndefinedAccount() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ShadowType accountType = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task,
                result).asObjectable();
        assertNotNull(accountType);
        display("Retrieved account shadow", accountType);

        DummyAccount dummyAccountBefore = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertFalse("Account is not disabled", dummyAccountBefore.isEnabled());

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationDeleteProperty(ShadowType.class,
                ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS,
                ActivationStatusType.DISABLED);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        result.computeStatus();
        display("modifyObject result", result);
        TestUtil.assertSuccess(result);

        delta.checkConsistence();
        // check if activation was changed
        DummyAccount dummyAccountAfter = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNull("Wrong dummy account " + transformNameFromResource(ACCOUNT_WILL_USERNAME) + " enabled flag",
                dummyAccountAfter.isEnabled());

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test154EnableAccount() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ShadowType accountType = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task,
                result).asObjectable();
        assertNotNull(accountType);
        display("Retrieved account shadow", accountType);

        DummyAccount dummyAccountBefore = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNull("Wrong dummy account enabled flag", dummyAccountBefore.isEnabled());

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
                ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS,
                ActivationStatusType.ENABLED);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);

        delta.checkConsistence();
        // check if activation was changed
        DummyAccount dummyAccountAfter = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertTrue("Dummy account " + transformNameFromResource(ACCOUNT_WILL_USERNAME) + " is disabled, expected enabled", dummyAccountAfter.isEnabled());

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test155SearchDisabledAccounts() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectQuery query = createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID, getDefaultAccountObjectClass(resourceBean));
        filterAnd(query.getFilter(),
                prismContext.queryFor(ShadowType.class)
                        .item(ShadowType.F_ACTIVATION, ActivationType.F_ADMINISTRATIVE_STATUS).eq(ActivationStatusType.DISABLED)
                        .buildFilter(), prismContext);

        syncServiceMock.reset();

        when();
        SearchResultList<PrismObject<ShadowType>> resultList =
                provisioningService.searchObjects(ShadowType.class, query, null, task, result);

        then();
        assertSuccess(result);

        assertEquals("Unexpected number of search results", 0, resultList.size());

        assertSteadyResource();
    }

    @Test
    public void test156SetValidFrom() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ShadowType accountType = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task,
                result).asObjectable();
        assertNotNull(accountType);

        display("Retrieved account shadow", accountType);

        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertTrue(dummyAccount.isEnabled());

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
                ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_VALID_FROM,
                XmlTypeConverter.createXMLGregorianCalendar(VALID_FROM_MILLIS));
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(),
                delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);

        delta.checkConsistence();
        // check if activation was changed
        dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertEquals("Wrong account validFrom in account " + transformNameFromResource(ACCOUNT_WILL_USERNAME), new Date(VALID_FROM_MILLIS), dummyAccount.getValidFrom());
        assertTrue("Dummy account " + transformNameFromResource(ACCOUNT_WILL_USERNAME) + " is disabled, expected enabled", dummyAccount.isEnabled());

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test157SetValidTo() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ShadowType accountType = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task,
                result).asObjectable();
        assertNotNull(accountType);

        display("Retrieved account shadow", accountType);

        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertTrue(dummyAccount.isEnabled());

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
                ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_VALID_TO,
                XmlTypeConverter.createXMLGregorianCalendar(VALID_TO_MILLIS));
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(),
                delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

        then();
        result.computeStatus();
        display("modifyObject result", result);
        TestUtil.assertSuccess(result);

        delta.checkConsistence();
        // check if activation was changed
        dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertEquals("Wrong account validFrom in account " + transformNameFromResource(ACCOUNT_WILL_USERNAME), new Date(VALID_FROM_MILLIS), dummyAccount.getValidFrom());
        assertEquals("Wrong account validTo in account " + transformNameFromResource(ACCOUNT_WILL_USERNAME), new Date(VALID_TO_MILLIS), dummyAccount.getValidTo());
        assertTrue("Dummy account " + transformNameFromResource(ACCOUNT_WILL_USERNAME) + " is disabled, expected enabled", dummyAccount.isEnabled());

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test158DeleteValidToValidFrom() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ShadowType accountType = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task,
                result).asObjectable();
        assertNotNull(accountType);

        display("Retrieved account shadow", accountType);

        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertTrue(dummyAccount.isEnabled());

        syncServiceMock.reset();

//        long millis = VALID_TO_MILLIS;

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationDeleteProperty(ShadowType.class,
                ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_VALID_TO,
                XmlTypeConverter.createXMLGregorianCalendar(VALID_TO_MILLIS));
        PrismObjectDefinition<ShadowType> def = accountType.asPrismObject().getDefinition();
        PropertyDelta<XMLGregorianCalendar> validFromDelta = prismContext.deltaFactory().property().createModificationDeleteProperty(
                SchemaConstants.PATH_ACTIVATION_VALID_FROM,
                def.findPropertyDefinition(SchemaConstants.PATH_ACTIVATION_VALID_FROM),
                XmlTypeConverter.createXMLGregorianCalendar(VALID_FROM_MILLIS));
        delta.addModification(validFromDelta);
        delta.checkConsistence();

        // WHEN
        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(),
                delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        then();
        result.computeStatus();
        display("modifyObject result", result);
        TestUtil.assertSuccess(result);

        delta.checkConsistence();
        // check if activation was changed
        dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNull("Unexpected account validTo in account " + transformNameFromResource(ACCOUNT_WILL_USERNAME) + ": " + dummyAccount.getValidTo(), dummyAccount.getValidTo());
        assertNull("Unexpected account validFrom in account " + transformNameFromResource(ACCOUNT_WILL_USERNAME) + ": " + dummyAccount.getValidFrom(), dummyAccount.getValidFrom());
        assertTrue("Dummy account " + transformNameFromResource(ACCOUNT_WILL_USERNAME) + " is disabled, expected enabled", dummyAccount.isEnabled());

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test159GetLockedoutAccount() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        dummyAccount.setLockout(true);

        XMLGregorianCalendar startTs = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        when();
        PrismObject<ShadowType> shadow =
                provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);

        // THEN
        then();
        result.computeStatus();
        display("getObject result", result);
        TestUtil.assertSuccess(result);

        XMLGregorianCalendar endTs = clock.currentTimeXMLGregorianCalendar();

        display("Retrieved account shadow", shadow);

        assertNotNull("No dummy account", shadow);

        if (supportsActivation()) {
            PrismAsserts.assertPropertyValue(shadow, SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS,
                    LockoutStatusType.LOCKED);
        } else {
            PrismAsserts.assertNoItem(shadow, SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS);
        }

        checkAccountWill(shadow, result, startTs, endTs);

        checkUniqueness(shadow);

        assertSteadyResource();
    }

    @Test
    public void test160SearchLockedAccounts() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectQuery query = createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID, getDefaultAccountObjectClass(resourceBean));
        filterAnd(query.getFilter(),
                prismContext.queryFor(ShadowType.class)
                        .item(ShadowType.F_ACTIVATION, ActivationType.F_LOCKOUT_STATUS).eq(LockoutStatusType.LOCKED)
                        .buildFilter(), prismContext);

        syncServiceMock.reset();

        // WHEN
        SearchResultList<PrismObject<ShadowType>> resultList =
                provisioningService.searchObjects(ShadowType.class, query, null, task, result);

        // THEN
        result.computeStatus();
        display(result);
        TestUtil.assertSuccess(result);

        assertEquals("Unexpected number of search results", 1, resultList.size());
        PrismObject<ShadowType> shadow = resultList.get(0);
        display("Shadow", shadow);
        assertShadowLockout(shadow, LockoutStatusType.LOCKED);

        assertSteadyResource();
    }

    @Test
    public void test162UnlockAccount() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ShadowType accountType = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task,
                result).asObjectable();
        assertNotNull(accountType);
        display("Retrieved account shadow", accountType);

        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertTrue("Account is not locked", dummyAccount.isLockout());

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
                ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS,
                LockoutStatusType.NORMAL);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        then();
        result.computeStatus();
        display("modifyObject result", result);
        TestUtil.assertSuccess(result);

        delta.checkConsistence();
        // check if activation was changed
        dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertFalse("Dummy account " + transformNameFromResource(ACCOUNT_WILL_USERNAME) + " is locked, expected unlocked", dummyAccount.isLockout());

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    @Test
    public void test163GetAccount() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        XMLGregorianCalendar startTs = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        PrismObject<ShadowType> shadow =
                provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);

        // THEN
        result.computeStatus();
        display("getObject result", result);
        TestUtil.assertSuccess(result);

        XMLGregorianCalendar endTs = clock.currentTimeXMLGregorianCalendar();

        display("Retrieved account shadow", shadow);

        assertNotNull("No dummy account", shadow);

        if (supportsActivation()) {
            PrismAsserts.assertPropertyValue(shadow, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS,
                    ActivationStatusType.ENABLED);
            PrismAsserts.assertPropertyValue(shadow, SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS,
                    LockoutStatusType.NORMAL);
        } else {
            PrismAsserts.assertNoItem(shadow, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS);
            PrismAsserts.assertNoItem(shadow, SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS);
        }

        checkAccountWill(shadow, result, startTs, endTs);

        checkUniqueness(shadow);

        assertSteadyResource();
    }

    @Test
    public void test163SearchLockedAccounts() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectQuery query = createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID, getDefaultAccountObjectClass(resourceBean));
        filterAnd(query.getFilter(),
                prismContext.queryFor(ShadowType.class)
                        .item(ShadowType.F_ACTIVATION, ActivationType.F_LOCKOUT_STATUS).eq(LockoutStatusType.LOCKED)
                        .buildFilter(), prismContext);

        syncServiceMock.reset();

        // WHEN
        SearchResultList<PrismObject<ShadowType>> resultList =
                provisioningService.searchObjects(ShadowType.class, query, null, task, result);

        // THEN
        result.computeStatus();
        display(result);
        TestUtil.assertSuccess(result);

        assertEquals("Unexpected number of search results", 0, resultList.size());

        assertSteadyResource();
    }

    @Test
    public void test170SearchNull() throws Exception {
        testSearchIterative(null, null, true, true, false,
                "meathook", "daemon", transformNameFromResource("morgan"), transformNameFromResource("Will"));
    }

    @Test
    public void test171SearchShipSeaMonkey() throws Exception {
        testSearchIterativeSingleAttrFilter(
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Sea Monkey", null, true,
                "meathook");
    }

    // See MID-1460
    @Test
    public void test172SearchShipNull() throws Exception {
        testSearchIterativeSingleAttrFilter(
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, null, null, true,
                "daemon");
    }

    @Test
    public void test173SearchWeaponCutlass() throws Exception {
        // Make sure there is an account on resource that the provisioning has
        // never seen before, so there is no shadow
        // for it yet.
        DummyAccount newAccount = new DummyAccount("carla");
        newAccount.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Carla");
        newAccount.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "Sea Monkey");
        newAccount.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME, "cutlass");
        newAccount.setEnabled(true);
        dummyResource.addAccount(newAccount);

        IntegrationTestTools.display("dummy", dummyResource.debugDump());

        testSearchIterativeSingleAttrFilter(
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME, "cutlass", null, true,
                transformNameFromResource("morgan"), "carla");
    }

    @Test
    public void test175SearchUidExact() throws Exception {
        dummyResource.setDisableNameHintChecks(true);
        testSearchIterativeSingleAttrFilter(
                SchemaConstants.ICFS_UID, willIcfUid, null, true,
                transformNameFromResource("Will"));
        dummyResource.setDisableNameHintChecks(false);
    }

    @Test
    public void test176SearchUidExactNoFetch() throws Exception {
        testSearchIterativeSingleAttrFilter(SchemaConstants.ICFS_UID, willIcfUid,
                GetOperationOptions.createNoFetch(), false,
                transformNameFromResource("Will"));
    }

    @Test
    public void test177SearchIcfNameRepoized() throws Exception {
        testSearchIterativeSingleAttrFilter(
                SchemaConstants.ICFS_NAME, getWillRepoIcfName(), null, true,
                transformNameFromResource(ACCOUNT_WILL_USERNAME));
    }

    @Test
    public void test180SearchNullPagingOffset0Size3() throws Exception {
        ObjectPaging paging = prismContext.queryFactory().createPaging(0, 3);
        paging.setOrdering(createAttributeOrdering(SchemaConstants.ICFS_NAME));
        SearchResultMetadata searchMetadata = testSearchIterativePaging(null, paging, null,
                getSortedUsernames18x(0, 3));
        assertApproxNumberOfAllResults(searchMetadata, getTest18xApproxNumberOfSearchResults());
    }

    /**
     * Reverse sort order, so we are sure that this thing is really sorting
     * and not just returning data in alphabetical order by default.
     */
    @Test
    public void test181SearchNullPagingOffset0Size3Desc() throws Exception {
        ObjectPaging paging = prismContext.queryFactory().createPaging(0, 3);
        paging.setOrdering(createAttributeOrdering(SchemaConstants.ICFS_NAME, OrderDirection.DESCENDING));
        SearchResultMetadata searchMetadata = testSearchIterativePaging(null, paging, null,
                getSortedUsernames18xDesc(0, 3));
        assertApproxNumberOfAllResults(searchMetadata, getTest18xApproxNumberOfSearchResults());
    }

    @Test
    public void test182SearchNullPagingOffset1Size2() throws Exception {
        ObjectPaging paging = prismContext.queryFactory().createPaging(1, 2);
        paging.setOrdering(createAttributeOrdering(SchemaConstants.ICFS_NAME));
        SearchResultMetadata searchMetadata = testSearchIterativePaging(null, paging, null,
                getSortedUsernames18x(1, 2));
        assertApproxNumberOfAllResults(searchMetadata, getTest18xApproxNumberOfSearchResults());
    }

    @Test
    public void test183SearchNullPagingOffset2Size3Desc() throws Exception {
        ObjectPaging paging = prismContext.queryFactory().createPaging(2, 3);
        paging.setOrdering(createAttributeOrdering(SchemaConstants.ICFS_NAME, OrderDirection.DESCENDING));
        SearchResultMetadata searchMetadata = testSearchIterativePaging(null, paging, null,
                getSortedUsernames18xDesc(2, 3));
        assertApproxNumberOfAllResults(searchMetadata, getTest18xApproxNumberOfSearchResults());
    }

    protected Integer getTest18xApproxNumberOfSearchResults() {
        return 5;
    }

    private String[] getSortedUsernames18x(int offset, int pageSize) {
        return Arrays.copyOfRange(getSortedUsernames18x(), offset, offset + pageSize);
    }

    @SuppressWarnings("SameParameterValue")
    private String[] getSortedUsernames18xDesc(int offset, int pageSize) {
        String[] usernames = getSortedUsernames18x();
        ArrayUtils.reverse(usernames);
        return Arrays.copyOfRange(usernames, offset, offset + pageSize);
    }

    protected String[] getSortedUsernames18x() {
        return new String[] { transformNameFromResource("Will"), "carla", "daemon", "meathook", transformNameFromResource("morgan") };
    }

    @SuppressWarnings("SameParameterValue")
    private ObjectOrdering createAttributeOrdering(QName attrQname) {
        return createAttributeOrdering(attrQname, OrderDirection.ASCENDING);
    }

    private ObjectOrdering createAttributeOrdering(QName attrQname, OrderDirection direction) {
        return prismContext.queryFactory().createOrdering(ItemPath.create(ShadowType.F_ATTRIBUTES, attrQname), direction);
    }

    @Test
    public void test194SearchIcfNameRepoizedNoFetch() throws Exception {
        testSearchIterativeSingleAttrFilter(SchemaConstants.ICFS_NAME, getWillRepoIcfName(),
                GetOperationOptions.createNoFetch(), false,
                transformNameFromResource(ACCOUNT_WILL_USERNAME));
    }

    @Test
    public void test195SearchIcfNameExact() throws Exception {
        testSearchIterativeSingleAttrFilter(
                SchemaConstants.ICFS_NAME, transformNameFromResource(ACCOUNT_WILL_USERNAME), null, true,
                transformNameFromResource(ACCOUNT_WILL_USERNAME));
    }

    @Test
    public void test196SearchIcfNameExactNoFetch() throws Exception {
        testSearchIterativeSingleAttrFilter(SchemaConstants.ICFS_NAME, transformNameFromResource(ACCOUNT_WILL_USERNAME),
                GetOperationOptions.createNoFetch(), false,
                transformNameFromResource(ACCOUNT_WILL_USERNAME));
    }

    @Test
    public void test197SearchIcfNameAndUidExactNoFetch() throws Exception {
        testSearchIterativeAlternativeAttrFilter(SchemaConstants.ICFS_NAME, transformNameFromResource(ACCOUNT_WILL_USERNAME),
                SchemaConstants.ICFS_UID, willIcfUid,
                GetOperationOptions.createNoFetch(), false,
                transformNameFromResource(ACCOUNT_WILL_USERNAME));
    }

    @Test
    public void test198SearchNone() throws Exception {
        ObjectFilter attrFilter = FilterCreationUtil.createNone(prismContext);
        testSearchIterative(attrFilter, null, true, true, false);
    }

    /**
     * Search with query that queries both the repository and the resource.
     * We cannot do this. This should fail.
     * MID-2822
     */
    @Test
    public void test199SearchOnAndOffResource() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectQuery query = createOnOffQuery();

        ResultHandler<ShadowType> handler = (object, parentResult) -> {
            AssertJUnit.fail("Handler called: " + object);
            return false;
        };

        when();
        try {
            provisioningService.searchObjectsIterative(ShadowType.class, query,
                    null, handler, task, result);

            AssertJUnit.fail("unexpected success");

        } catch (SchemaException e) {
            displayExpectedException(e);
        }

        then();
        assertFailure(result);
    }

    /**
     * Search with query that queries both the repository and the resource.
     * NoFetch. This should go OK.
     * MID-2822
     */
    @Test
    public void test196SearchOnAndOffResourceNoFetch() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectQuery query = createOnOffQuery();

        ResultHandler<ShadowType> handler = (object, parentResult) -> {
            AssertJUnit.fail("Handler called: " + object);
            return false;
        };

        when();
        provisioningService.searchObjectsIterative(ShadowType.class, query,
                SelectorOptions.createCollection(GetOperationOptions.createNoFetch()),
                handler, task, result);

        then();
        assertSuccess(result);
    }

    private ObjectQuery createOnOffQuery() throws SchemaException {
        ResourceSchema resourceSchema = ResourceSchemaFactory.getRawSchema(resource);
        assertNotNull(resourceSchema);
        ResourceObjectClassDefinition objectClassDef =
                resourceSchema.findObjectClassDefinitionRequired(RI_ACCOUNT_OBJECT_CLASS);
        ResourceAttributeDefinition<?> attrDef = objectClassDef.findAttributeDefinition(
                dummyResourceCtl.getAttributeQName(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME));
        assertNotNull(attrDef);

        ObjectQuery query = prismContext.queryFor(ShadowType.class)
                .item(ShadowType.F_RESOURCE_REF).ref(RESOURCE_DUMMY_OID)
                .and().item(ShadowType.F_OBJECT_CLASS).eq(new QName(MidPointConstants.NS_RI, SchemaConstants.ACCOUNT_OBJECT_CLASS_LOCAL_NAME))
                .and().itemWithDef(attrDef, ShadowType.F_ATTRIBUTES, attrDef.getItemName()).eq("Sea Monkey")
                .and().item(ShadowType.F_DEAD).eq(true)
                .build();
        displayDumpable("Query", query);
        return query;
    }

    @SuppressWarnings("SameParameterValue")
    private <T> void testSearchIterativeSingleAttrFilter(String attrName, T attrVal,
            GetOperationOptions rootOptions, boolean fullShadow, String... expectedAccountIds) throws Exception {
        testSearchIterativeSingleAttrFilter(dummyResourceCtl.getAttributeQName(attrName), attrVal,
                rootOptions, fullShadow, expectedAccountIds);
    }

    <T> void testSearchIterativeSingleAttrFilter(QName attrQName, T attrVal,
            GetOperationOptions rootOptions, boolean fullShadow, String... expectedAccountNames) throws Exception {
        ResourceSchema resourceSchema = requireNonNull(ResourceSchemaFactory.getRawSchema(resource));
        ResourceObjectClassDefinition objectClassDef =
                resourceSchema.findObjectClassDefinitionRequired(RI_ACCOUNT_OBJECT_CLASS);
        ResourceAttributeDefinition<?> attrDef = objectClassDef.findAttributeDefinitionRequired(attrQName);
        ObjectFilter filter = prismContext.queryFor(ShadowType.class)
                .itemWithDef(attrDef, ShadowType.F_ATTRIBUTES, attrDef.getItemName()).eq(attrVal)
                .buildFilter();
        testSearchIterative(filter, rootOptions, fullShadow, true, false, expectedAccountNames);
    }

    @SuppressWarnings("SameParameterValue")
    private <T> void testSearchIterativeAlternativeAttrFilter(QName attr1QName, T attr1Val,
            QName attr2QName, T attr2Val,
            GetOperationOptions rootOptions, boolean fullShadow, String... expectedAccountNames) throws Exception {
        ResourceSchema resourceSchema = requireNonNull(ResourceSchemaFactory.getRawSchema(resource));
        ResourceObjectClassDefinition objectClassDef =
                resourceSchema.findObjectClassDefinitionRequired(RI_ACCOUNT_OBJECT_CLASS);
        ResourceAttributeDefinition<?> attr1Def = objectClassDef.findAttributeDefinitionRequired(attr1QName);
        ResourceAttributeDefinition<?> attr2Def = objectClassDef.findAttributeDefinitionRequired(attr2QName);
        ObjectFilter filter = prismContext.queryFor(ShadowType.class)
                .itemWithDef(attr1Def, ShadowType.F_ATTRIBUTES, attr1Def.getItemName()).eq(attr1Val)
                .or().itemWithDef(attr2Def, ShadowType.F_ATTRIBUTES, attr2Def.getItemName()).eq(attr2Val)
                .buildFilter();
        testSearchIterative(filter, rootOptions, fullShadow, false, true, expectedAccountNames);
    }

    @SuppressWarnings("UnusedReturnValue")
    private SearchResultMetadata testSearchIterative(
            ObjectFilter attrFilter, GetOperationOptions rootOptions, final boolean fullShadow,
            boolean useObjectClassFilter, final boolean useRepo, String... expectedAccountNames)
            throws Exception {
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        ObjectQuery query;
        if (useObjectClassFilter) {
            query = createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID, RI_ACCOUNT_OBJECT_CLASS);
            if (attrFilter != null) {
                AndFilter filter = (AndFilter) query.getFilter();
                filter.getConditions().add(attrFilter);
            }
        } else {
            query = createResourceQuery(RESOURCE_DUMMY_OID);
            if (attrFilter != null) {
                query.setFilter(prismContext.queryFactory().createAnd(query.getFilter(), attrFilter));
            }
        }

        displayDumpable("Query", query);

        final List<PrismObject<ShadowType>> foundObjects = new ArrayList<>();
        ResultHandler<ShadowType> handler = (shadow, parentResult) -> {
            foundObjects.add(shadow);

            assertTrue(shadow.canRepresent(ShadowType.class));
            if (!useRepo) {
                try {
                    checkAccountShadow(shadow, parentResult, fullShadow);
                } catch (ConfigurationException | SchemaException e) {
                    throw new SystemException(e.getMessage(), e);
                }
            }
            return true;
        };

        Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions.createCollection(rootOptions);

        when();
        SearchResultMetadata searchMetadata;
        if (useRepo) {
            searchMetadata = repositoryService.searchObjectsIterative(ShadowType.class, query, handler, null, true, result);
        } else {
            searchMetadata = provisioningService.searchObjectsIterative(ShadowType.class, query, options, handler, task, result);
        }

        then();
        assertSuccess(result);

        display("found shadows", foundObjects);

        for (String expectedAccountId : expectedAccountNames) {
            boolean found = false;
            for (PrismObject<ShadowType> foundObject : foundObjects) {
                if (expectedAccountId.equals(foundObject.asObjectable().getName().getOrig())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                AssertJUnit.fail("Account " + expectedAccountId + " was expected to be found but it was not found (found " + foundObjects.size() + ", expected " + expectedAccountNames.length + ")");
            }
        }

        assertEquals("Wrong number of found objects (" + foundObjects + "): " + foundObjects, expectedAccountNames.length, foundObjects.size());
        if (!useRepo) {
            checkUniqueness(foundObjects);
        }
        assertSteadyResource();

        return searchMetadata;
    }

    // This has to be a different method than ordinary search. We care about ordering here.
    // Also, paged search without sorting does not make much sense anyway.
    @SuppressWarnings("SameParameterValue")
    private SearchResultMetadata testSearchIterativePaging(ObjectFilter attrFilter,
            ObjectPaging paging, GetOperationOptions rootOptions, String... expectedAccountNames)
            throws Exception {
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        ObjectQuery query = createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID, RI_ACCOUNT_OBJECT_CLASS);
        if (attrFilter != null) {
            AndFilter filter = (AndFilter) query.getFilter();
            filter.getConditions().add(attrFilter);
        }
        query.setPaging(paging);

        displayDumpable("Query", query);

        final List<PrismObject<ShadowType>> foundObjects = new ArrayList<>();
        ResultHandler<ShadowType> handler = (shadow, parentResult) -> {
            foundObjects.add(shadow);

            assertTrue(shadow.canRepresent(ShadowType.class));
            try {
                checkAccountShadow(shadow, parentResult, true);
            } catch (ConfigurationException | SchemaException e) {
                throw new SystemException(e.getMessage(), e);
            }
            return true;
        };

        Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions.createCollection(rootOptions);

        when();
        SearchResultMetadata searchMetadata =
                provisioningService.searchObjectsIterative(ShadowType.class, query, options, handler, task, result);

        then();
        assertSuccess(result);

        display("found shadows", foundObjects);

        int i = 0;
        for (String expectedAccountId : expectedAccountNames) {
            PrismObject<ShadowType> foundObject = foundObjects.get(i);
            if (!expectedAccountId.equals(foundObject.asObjectable().getName().getOrig())) {
                fail("Account " + expectedAccountId + " was expected to be found on " + i + " position, but it was not found (found " + foundObject.asObjectable().getName().getOrig() + ")");
            }
            i++;
        }

        assertEquals("Wrong number of found objects (" + foundObjects + "): " + foundObjects, expectedAccountNames.length, foundObjects.size());
        checkUniqueness(foundObjects);
        assertSteadyResource();

        return searchMetadata;
    }

    @Test
    public void test200AddGroup() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        PrismObject<ShadowType> group = prismContext.parseObject(GROUP_PIRATES_FILE);
        group.checkConsistence();

        rememberDummyResourceGroupMembersReadCount(null);

        display("Adding group", group);

        // WHEN
        String addedObjectOid = provisioningService.addObject(group, null, null, task, result);

        // THEN
        assertSuccess("addObject has failed (result)", result);
        assertEquals(GROUP_PIRATES_OID, addedObjectOid);

        group.checkConsistence();
        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        ShadowType groupRepoType = getShadowRepo(GROUP_PIRATES_OID).asObjectable();
        display("group from repo", groupRepoType);
        PrismAsserts.assertEqualsPolyString("Name not equal.", GROUP_PIRATES_NAME, groupRepoType.getName());
        assertEquals("Wrong kind (repo)", ShadowKindType.ENTITLEMENT, groupRepoType.getKind());

        syncServiceMock.assertSingleNotifySuccessOnly();
        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        PrismObject<ShadowType> groupProvisioning = provisioningService.getObject(ShadowType.class, GROUP_PIRATES_OID, null, task, result);
        display("group from provisioning", groupProvisioning);
        checkGroupPirates(groupProvisioning, result);
        piratesIcfUid = getIcfUid(groupRepoType);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Check if the group was created in the dummy resource

        DummyGroup dummyGroup = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertNotNull("No dummy group " + GROUP_PIRATES_NAME, dummyGroup);
        assertEquals("Description is wrong", "Scurvy pirates", dummyGroup.getAttributeValue("description"));
        assertTrue("The group is not enabled", dummyGroup.isEnabled());

        // Check if the shadow is still in the repo (e.g. that the consistency or sync haven't removed it)
        PrismObject<ShadowType> shadowFromRepo = getShadowRepo(addedObjectOid);
        assertNotNull("Shadow was not created in the repository", shadowFromRepo);
        displayValue("Repository shadow", shadowFromRepo.debugDump());

        checkRepoEntitlementShadow(shadowFromRepo);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        checkUniqueness(group);
        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        assertSteadyResource();
    }

    @Test
    public void test202GetGroup() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        rememberDummyResourceGroupMembersReadCount(null);

        // WHEN
        PrismObject<ShadowType> shadow =
                provisioningService.getObject(ShadowType.class, GROUP_PIRATES_OID, null, task, result);

        // THEN
        assertSuccess(result);

        display("Retrieved group shadow", shadow);

        assertNotNull("No dummy group", shadow);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        checkGroupPirates(shadow, result);

        checkUniqueness(shadow);

        assertSteadyResource();
    }

    private void checkGroupPirates(PrismObject<ShadowType> shadow, OperationResult result)
            throws SchemaException, ConfigurationException {
        checkGroupShadow(shadow, result);
        PrismAsserts.assertEqualsPolyString("Name not equal.", transformNameFromResource(GROUP_PIRATES_NAME), shadow.getName());
        assertEquals("Wrong kind (provisioning)", ShadowKindType.ENTITLEMENT, shadow.asObjectable().getKind());
        assertAttribute(shadow, DummyResourceContoller.DUMMY_GROUP_ATTRIBUTE_DESCRIPTION, "Scurvy pirates");
        Collection<ResourceAttribute<?>> attributes = ShadowUtil.getAttributes(shadow);
        assertEquals("Unexpected number of attributes", 3, attributes.size());

        assertNull("The _PASSWORD_ attribute sneaked into shadow", ShadowUtil.getAttributeValues(
                shadow, new QName(SchemaConstants.NS_ICF_SCHEMA, "password")));
    }

    @Test
    public void test203GetGroupNoFetch() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        GetOperationOptions rootOptions = new GetOperationOptions();
        rootOptions.setNoFetch(true);
        Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions.createCollection(rootOptions);

        rememberDummyResourceGroupMembersReadCount(null);

        // WHEN
        PrismObject<ShadowType> shadow =
                provisioningService.getObject(ShadowType.class, GROUP_PIRATES_OID, options, task, result);

        // THEN
        assertSuccess(result);

        display("Retrieved group shadow", shadow);

        assertNotNull("No dummy group", shadow);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        checkGroupShadow(shadow, result, false);

        checkUniqueness(shadow);

        assertSteadyResource();
    }

    @Test
    public void test205ModifyGroupReplace() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(
                ShadowType.class, GROUP_PIRATES_OID, DUMMY_GROUP_ATTRIBUTE_DESCRIPTION_PATH, "Bloodthirsty pirates");
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        assertSuccess(result);

        delta.checkConsistence();
        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertDummyAttributeValues(group, DummyResourceContoller.DUMMY_GROUP_ATTRIBUTE_DESCRIPTION, "Bloodthirsty pirates");

        if (isPreFetchResource()) {
            assertDummyResourceGroupMembersReadCountIncrement(null, 1);
        } else {
            assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        }

        syncServiceMock.assertSingleNotifySuccessOnly();
        assertSteadyResource();
    }

    @Test
    public void test210AddPrivilege() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        PrismObject<ShadowType> priv = prismContext.parseObject(PRIVILEGE_PILLAGE_FILE);
        priv.checkConsistence();

        display("Adding priv", priv);

        // WHEN
        String addedObjectOid = provisioningService.addObject(priv, null, null, task, result);

        // THEN
        assertSuccess("addObject has failed (result)", result);
        assertEquals(PRIVILEGE_PILLAGE_OID, addedObjectOid);

        priv.checkConsistence();

        ShadowType groupRepoType = getShadowRepo(PRIVILEGE_PILLAGE_OID).asObjectable();
        PrismAsserts.assertEqualsPolyString("Name not equal.", PRIVILEGE_PILLAGE_NAME, groupRepoType.getName());
        assertEquals("Wrong kind (repo)", ShadowKindType.ENTITLEMENT, groupRepoType.getKind());

        syncServiceMock.assertSingleNotifySuccessOnly();

        PrismObject<ShadowType> privProvisioning = provisioningService.getObject(ShadowType.class,
                PRIVILEGE_PILLAGE_OID, null, task, result);
        display("priv from provisioning", privProvisioning);
        checkPrivPillage(privProvisioning, result);
        pillageIcfUid = getIcfUid(privProvisioning);

        // Check if the priv was created in the dummy resource

        DummyPrivilege dummyPriv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("No dummy priv " + PRIVILEGE_PILLAGE_NAME, dummyPriv);
        assertEquals("Wrong privilege power", (Integer) 100, dummyPriv.getAttributeValue(DummyResourceContoller.DUMMY_PRIVILEGE_ATTRIBUTE_POWER, Integer.class));

        // Check if the shadow is still in the repo (e.g. that the consistency or sync haven't removed it)
        PrismObject<ShadowType> shadowFromRepo = getShadowRepo(addedObjectOid);
        assertNotNull("Shadow was not created in the repository", shadowFromRepo);
        displayValue("Repository shadow", shadowFromRepo.debugDump());

        checkRepoEntitlementShadow(shadowFromRepo);

        checkUniqueness(priv);
        assertSteadyResource();
    }

    @Test
    public void test212GetPriv() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        // WHEN
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, PRIVILEGE_PILLAGE_OID, null, task, result);

        // THEN
        assertSuccess(result);

        display("Retrieved priv shadow", shadow);

        assertNotNull("No dummy priv", shadow);

        checkPrivPillage(shadow, result);

        checkUniqueness(shadow);

        assertSteadyResource();
    }

    private void checkPrivPillage(PrismObject<ShadowType> shadow, OperationResult result)
            throws SchemaException, ConfigurationException {
        checkEntitlementShadow(shadow, result, OBJECTCLASS_PRIVILEGE_LOCAL_NAME, true);
        assertShadowName(shadow, PRIVILEGE_PILLAGE_NAME);
        assertEquals("Wrong kind (provisioning)", ShadowKindType.ENTITLEMENT, shadow.asObjectable().getKind());
        Collection<ResourceAttribute<?>> attributes = ShadowUtil.getAttributes(shadow);
        assertEquals("Unexpected number of attributes", 3, attributes.size());
        assertAttribute(shadow, DummyResourceContoller.DUMMY_PRIVILEGE_ATTRIBUTE_POWER, 100);

        assertNull("The _PASSWORD_ attribute sneaked into shadow", ShadowUtil.getAttributeValues(
                shadow, new QName(SchemaConstants.NS_ICF_SCHEMA, "password")));
    }

    @Test
    public void test214AddPrivilegeBargain() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        PrismObject<ShadowType> priv = prismContext.parseObject(PRIVILEGE_BARGAIN_FILE);
        priv.checkConsistence();

        rememberDummyResourceGroupMembersReadCount(null);

        display("Adding priv", priv);

        // WHEN
        String addedObjectOid = provisioningService.addObject(priv, null, null, task, result);

        // THEN
        assertSuccess("addObject has failed (result)", result);
        assertEquals(PRIVILEGE_BARGAIN_OID, addedObjectOid);

        priv.checkConsistence();
        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        ShadowType groupRepoType = getShadowRepo(PRIVILEGE_BARGAIN_OID).asObjectable();
        PrismAsserts.assertEqualsPolyString("Name not equal.", PRIVILEGE_BARGAIN_NAME, groupRepoType.getName());
        assertEquals("Wrong kind (repo)", ShadowKindType.ENTITLEMENT, groupRepoType.getKind());

        syncServiceMock.assertSingleNotifySuccessOnly();
        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        PrismObject<ShadowType> privProvisioningType = provisioningService.getObject(ShadowType.class,
                PRIVILEGE_BARGAIN_OID, null, task, result);
        display("priv from provisioning", privProvisioningType);
        checkPrivBargain(privProvisioningType, result);
        bargainIcfUid = getIcfUid(privProvisioningType);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Check if the group was created in the dummy resource

        DummyPrivilege dummyPriv = getDummyPrivilegeAssert(PRIVILEGE_BARGAIN_NAME, bargainIcfUid);
        assertNotNull("No dummy priv " + PRIVILEGE_BARGAIN_NAME, dummyPriv);

        // Check if the shadow is still in the repo (e.g. that the consistency or sync haven't removed it)
        PrismObject<ShadowType> shadowFromRepo = getShadowRepo(addedObjectOid);
        assertNotNull("Shadow was not created in the repository", shadowFromRepo);
        displayValue("Repository shadow", shadowFromRepo.debugDump());

        checkRepoEntitlementShadow(shadowFromRepo);

        checkUniqueness(priv);
        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        assertSteadyResource();
    }

    private void checkPrivBargain(PrismObject<ShadowType> shadow, OperationResult result)
            throws SchemaException, ConfigurationException {
        checkEntitlementShadow(shadow, result, OBJECTCLASS_PRIVILEGE_LOCAL_NAME, true);
        assertShadowName(shadow, PRIVILEGE_BARGAIN_NAME);
        assertEquals("Wrong kind (provisioning)", ShadowKindType.ENTITLEMENT, shadow.asObjectable().getKind());
        Collection<ResourceAttribute<?>> attributes = ShadowUtil.getAttributes(shadow);
        assertEquals("Unexpected number of attributes", 2, attributes.size());

        assertNull("The _PASSWORD_ attribute sneaked into shadow", ShadowUtil.getAttributeValues(
                shadow, new QName(SchemaConstants.NS_ICF_SCHEMA, "password")));
    }

    @Test
    public void test220EntitleAccountWillPirates() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createEntitleDelta(ACCOUNT_WILL_OID, DUMMY_ENTITLEMENT_GROUP_QNAME, GROUP_PIRATES_OID);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);

        delta.checkConsistence();
        if (isPreFetchResource()) {
            assertDummyResourceGroupMembersReadCountIncrement(null, 1);
        } else {
            assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        }

        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertMember(group, transformNameToResource(ACCOUNT_WILL_USERNAME));

        syncServiceMock.assertSingleNotifySuccessOnly();
        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        assertSteadyResource();
    }

    /**
     * Reads the will accounts, checks that the entitlement is there.
     */
    @Test
    public void test221GetPirateWill() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        // WHEN
        PrismObject<ShadowType> account = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);

        // THEN
        display("Account", account);
        assertSuccess(result);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        assertEntitlementGroup(account, GROUP_PIRATES_OID);

        // Just make sure nothing has changed
        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertMember(group, transformNameToResource(ACCOUNT_WILL_USERNAME));

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        assertSteadyResource();
    }

    @Test
    public void test222EntitleAccountWillPillage() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createEntitleDelta(ACCOUNT_WILL_OID, ASSOCIATION_PRIV_NAME, PRIVILEGE_PILLAGE_OID);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        assertSuccess(result);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNotNull("Account will is gone!", dummyAccount);
        Set<String> accountPrivileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("account privileges", accountPrivileges, PRIVILEGE_PILLAGE_NAME);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);

        delta.checkConsistence();
        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Make sure that the groups is still there and will is a member
        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertMember(group, transformNameToResource(ACCOUNT_WILL_USERNAME));

        syncServiceMock.assertSingleNotifySuccessOnly();
        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);
        display("Shadow after", shadow);
        assertEntitlementGroup(shadow, GROUP_PIRATES_OID);
        assertEntitlementPriv(shadow, PRIVILEGE_PILLAGE_OID);

        assertSteadyResource();
    }

    @Test
    public void test223EntitleAccountWillBargain() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createEntitleDelta(ACCOUNT_WILL_OID, ASSOCIATION_PRIV_NAME, PRIVILEGE_BARGAIN_OID);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        assertSuccess(result);

        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNotNull("Account will is gone!", dummyAccount);
        Set<String> accountPrivileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("account privileges", accountPrivileges, PRIVILEGE_PILLAGE_NAME, PRIVILEGE_BARGAIN_NAME);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object (pillage) is gone!", priv);
        DummyPrivilege priv2 = getDummyPrivilegeAssert(PRIVILEGE_BARGAIN_NAME, bargainIcfUid);
        assertNotNull("Privilege object (bargain) is gone!", priv2);

        delta.checkConsistence();

        // Make sure that the groups is still there and will is a member
        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertMember(group, transformNameToResource(ACCOUNT_WILL_USERNAME));

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    /**
     * Reads the will accounts, checks that both entitlements are there.
     */
    @Test
    public void test224GetPillagingPirateWill() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        // WHEN
        PrismObject<ShadowType> account = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);

        // THEN
        display("Account", account);
        assertSuccess(result);

        assertEntitlementGroup(account, GROUP_PIRATES_OID);
        assertEntitlementPriv(account, PRIVILEGE_PILLAGE_OID);
        assertEntitlementPriv(account, PRIVILEGE_BARGAIN_OID);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Just make sure nothing has changed
        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNotNull("Account will is gone!", dummyAccount);
        Set<String> accountPrivileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("Wrong account privileges", accountPrivileges, PRIVILEGE_PILLAGE_NAME, PRIVILEGE_BARGAIN_NAME);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);
        DummyPrivilege priv2 = getDummyPrivilegeAssert(PRIVILEGE_BARGAIN_NAME, bargainIcfUid);
        assertNotNull("Privilege object (bargain) is gone!", priv2);

        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertMember(group, transformNameToResource(ACCOUNT_WILL_USERNAME));

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        assertSteadyResource();
    }

    /**
     * Create a fresh group directly on the resource. So we are sure there is no shadow
     * for it yet. Add will to this group. Get will account. Make sure that the group is
     * in the associations.
     */
    @Test
    public void test225GetFoolishPirateWill() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        DummyGroup groupFools = new DummyGroup("fools");
        dummyResource.addGroup(groupFools);
        groupFools.addMember(transformNameFromResource(ACCOUNT_WILL_USERNAME));

        syncServiceMock.reset();
        rememberDummyResourceGroupMembersReadCount(null);
        rememberCounter(InternalCounters.CONNECTOR_OPERATION_COUNT);

        // WHEN
        PrismObject<ShadowType> account = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);

        // THEN
        display("Account", account);
        assertSuccess(result);

        assertCounterIncrement(InternalCounters.CONNECTOR_OPERATION_COUNT, 2);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        PrismObject<ShadowType> foolsShadow = findShadowByName(new QName(RESOURCE_DUMMY_NS, OBJECTCLASS_GROUP_LOCAL_NAME), "fools", resource, result);
        assertNotNull("No shadow for group fools", foolsShadow);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        assertEntitlementGroup(account, GROUP_PIRATES_OID);
        assertEntitlementGroup(account, foolsShadow.getOid());
        assertEntitlementPriv(account, PRIVILEGE_PILLAGE_OID);
        assertEntitlementPriv(account, PRIVILEGE_BARGAIN_OID);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Just make sure nothing has changed
        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNotNull("Account will is gone!", dummyAccount);
        Set<String> accountPrivileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("Wrong account privileges", accountPrivileges, PRIVILEGE_PILLAGE_NAME, PRIVILEGE_BARGAIN_NAME);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);
        DummyPrivilege priv2 = getDummyPrivilegeAssert(PRIVILEGE_BARGAIN_NAME, bargainIcfUid);
        assertNotNull("Privilege object (bargain) is gone!", priv2);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertMember(group, transformNameToResource(ACCOUNT_WILL_USERNAME));

        String foolsIcfUid = getIcfUid(foolsShadow);
        groupFools = getDummyGroupAssert("fools", foolsIcfUid);
        assertMember(groupFools, transformNameToResource(ACCOUNT_WILL_USERNAME));

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        assertSteadyResource();
    }

    /**
     * Make the account point to a privilege that does not exist.
     * MidPoint should ignore such privilege.
     */
    @Test
    public void test226WillNonsensePrivilege() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        dummyAccount.addAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, PRIVILEGE_NONSENSE_NAME);

        syncServiceMock.reset();

        // WHEN
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);

        // THEN
        assertSuccess(result);
        assertCounterIncrement(InternalCounters.CONNECTOR_OPERATION_COUNT, 3);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        PrismObject<ShadowType> foolsShadow = findShadowByName(new QName(RESOURCE_DUMMY_NS, OBJECTCLASS_GROUP_LOCAL_NAME), "fools", resource, result);
        assertNotNull("No shadow for group fools", foolsShadow);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        assertEntitlementGroup(shadow, GROUP_PIRATES_OID);
        assertEntitlementGroup(shadow, foolsShadow.getOid());
        assertEntitlementPriv(shadow, PRIVILEGE_PILLAGE_OID);
        assertEntitlementPriv(shadow, PRIVILEGE_BARGAIN_OID);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Just make sure nothing has changed
        dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNotNull("Account will is gone!", dummyAccount);
        Set<String> accountProvileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("Wrong account privileges", accountProvileges,
                PRIVILEGE_PILLAGE_NAME, PRIVILEGE_BARGAIN_NAME, PRIVILEGE_NONSENSE_NAME);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);
        DummyPrivilege priv2 = getDummyPrivilegeAssert(PRIVILEGE_BARGAIN_NAME, bargainIcfUid);
        assertNotNull("Privilege object (bargain) is gone!", priv2);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertMember(group, transformNameToResource(ACCOUNT_WILL_USERNAME));

        String foolsIcfUid = getIcfUid(foolsShadow);
        DummyGroup groupFools = getDummyGroupAssert("fools", foolsIcfUid);
        assertMember(groupFools, transformNameToResource(ACCOUNT_WILL_USERNAME));

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        assertSteadyResource();
    }

    @Test
    public void test230DetitleAccountWillPirates() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createDetitleDelta(ACCOUNT_WILL_OID, DUMMY_ENTITLEMENT_GROUP_QNAME, GROUP_PIRATES_OID);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);
        delta.checkConsistence();
        assertAccountPiratesDetitled();
    }

    /**
     * Entitle will to pirates. But this time use identifiers instead of OID.
     * Relates to MID-5315
     */
    @Test
    public void test232EntitleAccountWillPiratesIdentifiersName() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createEntitleDeltaIdentifiers(
                ACCOUNT_WILL_OID, DUMMY_ENTITLEMENT_GROUP_QNAME, SchemaConstants.ICFS_NAME, GROUP_PIRATES_NAME);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);
        delta.checkConsistence();
        assertAccountPiratesEntitled();
    }

    /**
     * Detitle will to pirates. But this time use identifiers instead of OID.
     * MID-5315
     */
    @Test
    public void test233DetitleAccountWillPiratesIdentifiersName() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createDetitleDeltaIdentifiers(
                ACCOUNT_WILL_OID, DUMMY_ENTITLEMENT_GROUP_QNAME, SchemaConstants.ICFS_NAME, GROUP_PIRATES_NAME);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);
        delta.checkConsistence();
        assertAccountPiratesDetitled();
    }

    /**
     * Entitle will to pirates. But this time use identifiers instead of OID.
     * Relates to MID-5315
     */
    @Test
    public void test234EntitleAccountWillPiratesIdentifiersUid() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createEntitleDeltaIdentifiers(
                ACCOUNT_WILL_OID, DUMMY_ENTITLEMENT_GROUP_QNAME, SchemaConstants.ICFS_UID, piratesIcfUid);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);
        delta.checkConsistence();
        assertAccountPiratesEntitled();
    }

    /**
     * Detitle will to pirates. But this time use identifiers instead of OID.
     * MID-5315
     */
    @Test
    public void test235DetitleAccountWillPiratesIdentifiersUid() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        rememberDummyResourceGroupMembersReadCount(null);
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createDetitleDeltaIdentifiers(
                ACCOUNT_WILL_OID, DUMMY_ENTITLEMENT_GROUP_QNAME, SchemaConstants.ICFS_UID, piratesIcfUid);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);
        delta.checkConsistence();
        assertAccountPiratesDetitled();
    }

    private void assertAccountPiratesDetitled() throws Exception {
        if (isPreFetchResource()) {
            assertDummyResourceGroupMembersReadCountIncrement(null, 1);
        } else {
            assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        }

        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertNoMember(group, getWillRepoIcfName());

        // Make sure that account is still there and it has the privilege
        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNotNull("Account will is gone!", dummyAccount);
        Set<String> accountProvileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("Wrong account privileges", accountProvileges,
                PRIVILEGE_PILLAGE_NAME, PRIVILEGE_BARGAIN_NAME, PRIVILEGE_NONSENSE_NAME);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);
        DummyPrivilege priv2 = getDummyPrivilegeAssert(PRIVILEGE_BARGAIN_NAME, bargainIcfUid);
        assertNotNull("Privilege object (bargain) is gone!", priv2);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        syncServiceMock.assertSingleNotifySuccessOnly();

        Task task = getTestTask();
        OperationResult result = task.getResult();
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);
        display("Shadow after", shadow);
        assertEntitlementPriv(shadow, PRIVILEGE_PILLAGE_OID);
        assertEntitlementPriv(shadow, PRIVILEGE_BARGAIN_OID);

        assertSteadyResource();
    }

    private void assertAccountPiratesEntitled() throws Exception {
        if (isPreFetchResource()) {
            assertDummyResourceGroupMembersReadCountIncrement(null, 1);
        } else {
            assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        }

        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertMember(group, transformNameFromResource(ACCOUNT_WILL_USERNAME));

        // Make sure that account is still there and it has the privilege
        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNotNull("Account will is gone!", dummyAccount);
        Set<String> accountPrivileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("Wrong account privileges", accountPrivileges,
                PRIVILEGE_PILLAGE_NAME, PRIVILEGE_BARGAIN_NAME, PRIVILEGE_NONSENSE_NAME);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);
        DummyPrivilege priv2 = getDummyPrivilegeAssert(PRIVILEGE_BARGAIN_NAME, bargainIcfUid);
        assertNotNull("Privilege object (bargain) is gone!", priv2);

        assertDummyResourceGroupMembersReadCountIncrement(null, 0);
        syncServiceMock.assertSingleNotifySuccessOnly();

        Task task = getTestTask();
        OperationResult result = task.getResult();
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);
        display("Shadow after", shadow);
        assertEntitlementGroup(shadow, GROUP_PIRATES_OID);
        assertEntitlementPriv(shadow, PRIVILEGE_PILLAGE_OID);
        assertEntitlementPriv(shadow, PRIVILEGE_BARGAIN_OID);

        assertSteadyResource();
    }

    @Test
    public void test238DetitleAccountWillPillage() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createDetitleDelta(ACCOUNT_WILL_OID, ASSOCIATION_PRIV_NAME, PRIVILEGE_PILLAGE_OID);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        assertSuccess(result);

        delta.checkConsistence();
        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertNoMember(group, getWillRepoIcfName());

        // Make sure that account is still there and it has the privilege
        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNotNull("Account will is gone!", dummyAccount);
        Set<String> accountPrivileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("Wrong account privileges", accountPrivileges,
                PRIVILEGE_BARGAIN_NAME, PRIVILEGE_NONSENSE_NAME);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);

        syncServiceMock.assertSingleNotifySuccessOnly();

        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, ACCOUNT_WILL_OID, null, task, result);
        display("Shadow after", shadow);
        assertEntitlementPriv(shadow, PRIVILEGE_BARGAIN_OID);

        assertSteadyResource();
    }

    @Test
    public void test239DetitleAccountWillBargain() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createDetitleDelta(ACCOUNT_WILL_OID, ASSOCIATION_PRIV_NAME, PRIVILEGE_BARGAIN_OID);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        // WHEN
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        // THEN
        assertSuccess(result);

        delta.checkConsistence();
        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertNoMember(group, getWillRepoIcfName());

        // Make sure that account is still there and it has the privilege
        DummyAccount dummyAccount = getDummyAccountAssert(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid);
        assertNotNull("Account will is gone!", dummyAccount);
        Set<String> accountPrivileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("Wrong account privileges", accountPrivileges, PRIVILEGE_NONSENSE_NAME);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);
        DummyPrivilege priv2 = getDummyPrivilegeAssert(PRIVILEGE_BARGAIN_NAME, bargainIcfUid);
        assertNotNull("Privilege object (bargain) is gone!", priv2);

        syncServiceMock.assertSingleNotifySuccessOnly();
        assertSteadyResource();
    }

    /**
     * LeChuck has both group and priv entitlement. Let's add him together with these entitlements.
     */
    @Test
    public void test260AddAccountLeChuck() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        PrismObject<ShadowType> accountBefore = prismContext.parseObject(ACCOUNT_LECHUCK_FILE);
        accountBefore.checkConsistence();

        display("Adding shadow", accountBefore);

        // WHEN
        String addedObjectOid = provisioningService.addObject(accountBefore, null, null, task, result);

        // THEN
        assertSuccess("addObject has failed (result)", result);
        assertEquals(ACCOUNT_LECHUCK_OID, addedObjectOid);

        accountBefore.checkConsistence();

        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, addedObjectOid, null, task, result);
        leChuckIcfUid = getIcfUid(shadow);

        // Check if the account was created in the dummy resource and that it has the entitlements

        DummyAccount dummyAccount = getDummyAccountAssert(ACCOUNT_LECHUCK_NAME, leChuckIcfUid);
        assertNotNull("No dummy account", dummyAccount);
        assertEquals("Fullname is wrong", "LeChuck", dummyAccount.getAttributeValue(DummyAccount.ATTR_FULLNAME_NAME));
        assertTrue("The account is not enabled", dummyAccount.isEnabled());
        assertEquals("Wrong password", "und3ad", dummyAccount.getPassword());

        Set<String> accountPrivileges = dummyAccount.getAttributeValues(DummyAccount.ATTR_PRIVILEGES_NAME, String.class);
        PrismAsserts.assertSets("account privileges", accountPrivileges, PRIVILEGE_PILLAGE_NAME);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);

        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertMember(group, transformNameFromResource(ACCOUNT_LECHUCK_NAME));

        PrismObject<ShadowType> repoAccount = getShadowRepo(ACCOUNT_LECHUCK_OID);
        assertShadowName(repoAccount, ACCOUNT_LECHUCK_NAME);
        assertEquals("Wrong kind (repo)", ShadowKindType.ACCOUNT, repoAccount.asObjectable().getKind());
        assertAttribute(repoAccount, SchemaConstants.ICFS_NAME, ACCOUNT_LECHUCK_NAME);
        if (isIcfNameUidSame()) {
            assertAttribute(repoAccount, SchemaConstants.ICFS_UID, ACCOUNT_LECHUCK_NAME);
        } else {
            assertAttribute(repoAccount, SchemaConstants.ICFS_UID, dummyAccount.getId());
        }

        syncServiceMock.assertSingleNotifySuccessOnly();

        PrismObject<ShadowType> provisioningAccount = provisioningService.getObject(ShadowType.class,
                ACCOUNT_LECHUCK_OID, null, task, result);
        display("account from provisioning", provisioningAccount);
        assertShadowName(provisioningAccount, ACCOUNT_LECHUCK_NAME);
        assertEquals("Wrong kind (provisioning)", ShadowKindType.ACCOUNT, provisioningAccount.asObjectable().getKind());
        assertAttribute(provisioningAccount, SchemaConstants.ICFS_NAME, transformNameFromResource(ACCOUNT_LECHUCK_NAME));
        if (isIcfNameUidSame()) {
            assertAttribute(provisioningAccount, SchemaConstants.ICFS_UID, transformNameFromResource(ACCOUNT_LECHUCK_NAME));
        } else {
            assertAttribute(provisioningAccount, SchemaConstants.ICFS_UID, dummyAccount.getId());
        }

        assertEntitlementGroup(provisioningAccount, GROUP_PIRATES_OID);
        assertEntitlementPriv(provisioningAccount, PRIVILEGE_PILLAGE_OID);

        assertNull("The _PASSWORD_ attribute sneaked into shadow", ShadowUtil.getAttributeValues(
                provisioningAccount, new QName(SchemaConstants.NS_ICF_SCHEMA, "password")));

        checkUniqueness(provisioningAccount);

        assertSteadyResource();
    }

    /**
     * LeChuck has both group and priv entitlement. If deleted it should be correctly removed from all
     * the entitlements.
     */
    @Test
    public void test265DeleteAccountLeChuck() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        // WHEN
        provisioningService.deleteObject(ShadowType.class, ACCOUNT_LECHUCK_OID, null, null, task, result);

        // THEN
        assertSuccess("addObject has failed (result)", result);
        syncServiceMock.assertSingleNotifySuccessOnly();

        // Check if the account is gone and that group membership is gone as well

        DummyAccount dummyAccount = getDummyAccount(ACCOUNT_LECHUCK_NAME, leChuckIcfUid);
        assertNull("Dummy account is NOT gone", dummyAccount);

        // Make sure that privilege object is still there
        DummyPrivilege priv = getDummyPrivilegeAssert(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNotNull("Privilege object is gone!", priv);

        DummyGroup group = getDummyGroupAssert(GROUP_PIRATES_NAME, piratesIcfUid);
        assertNoMember(group, ACCOUNT_LECHUCK_NAME);

        try {
            repositoryService.getObject(ShadowType.class, ACCOUNT_LECHUCK_OID, GetOperationOptions.createRawCollection(), result);

            AssertJUnit.fail("Shadow (repo) is not gone");
        } catch (ObjectNotFoundException e) {
            // This is expected
        }

        try {
            provisioningService.getObject(ShadowType.class, ACCOUNT_LECHUCK_OID, null, task, result);

            AssertJUnit.fail("Shadow (provisioning) is not gone");
        } catch (ObjectNotFoundException e) {
            // This is expected
        }

        assertSteadyResource();
    }

    // test28x in TestDummyCaseIgnore

    @Test
    public void test298DeletePrivPillage() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();

        // WHEN
        provisioningService.deleteObject(ShadowType.class, PRIVILEGE_PILLAGE_OID, null, null, task, result);

        // THEN
        assertSuccess(result);

        syncServiceMock.assertSingleNotifySuccessOnly();

        try {
            repositoryService.getObject(ShadowType.class, PRIVILEGE_PILLAGE_OID, GetOperationOptions.createRawCollection(), result);
            AssertJUnit.fail("Priv shadow is not gone (repo)");
        } catch (ObjectNotFoundException e) {
            // This is expected
        }

        try {
            provisioningService.getObject(ShadowType.class, PRIVILEGE_PILLAGE_OID, null, task, result);
            AssertJUnit.fail("Priv shadow is not gone (provisioning)");
        } catch (ObjectNotFoundException e) {
            // This is expected
        }

        DummyPrivilege priv = getDummyPrivilege(PRIVILEGE_PILLAGE_NAME, pillageIcfUid);
        assertNull("Privilege object NOT is gone", priv);

        assertSteadyResource();
    }

    @Test
    public void test299DeleteGroupPirates() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();

        // WHEN
        provisioningService.deleteObject(ShadowType.class, GROUP_PIRATES_OID, null, null, task, result);

        // THEN
        assertSuccess(result);

        syncServiceMock.assertSingleNotifySuccessOnly();

        try {
            repositoryService.getObject(ShadowType.class, GROUP_PIRATES_OID, GetOperationOptions.createRawCollection(), result);
            AssertJUnit.fail("Group shadow is not gone (repo)");
        } catch (ObjectNotFoundException e) {
            // This is expected
        }

        try {
            provisioningService.getObject(ShadowType.class, GROUP_PIRATES_OID, null, task, result);
            AssertJUnit.fail("Group shadow is not gone (provisioning)");
        } catch (ObjectNotFoundException e) {
            // This is expected
        }

        DummyGroup dummyAccount = getDummyGroup(GROUP_PIRATES_NAME, piratesIcfUid);
        assertNull("Dummy group '" + GROUP_PIRATES_NAME + "' is not gone from dummy resource", dummyAccount);

        assertSteadyResource();
    }

    @Test
    public void test300AccountRename() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
                ACCOUNT_MORGAN_OID, SchemaConstants.ICFS_NAME_PATH, ACCOUNT_CPTMORGAN_NAME);
        provisioningService.applyDefinition(delta, task, result);
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);

        delta.checkConsistence();
        PrismObject<ShadowType> account = provisioningService.getObject(ShadowType.class, ACCOUNT_MORGAN_OID, null, task, result);
        Collection<ResourceAttribute<?>> identifiers = ShadowUtil.getPrimaryIdentifiers(account);
        assertNotNull("Identifiers must not be null", identifiers);
        assertEquals("Expected one identifier", 1, identifiers.size());

        ResourceAttribute<?> identifier = identifiers.iterator().next();

        String shadowUuid = ACCOUNT_CPTMORGAN_NAME;

        assertDummyAccountAttributeValues(shadowUuid, morganIcfUid,
                DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Captain Morgan");

        PrismObject<ShadowType> repoShadow = getShadowRepo(ACCOUNT_MORGAN_OID);
        assertAccountShadowRepo(repoShadow, ACCOUNT_MORGAN_OID, ACCOUNT_CPTMORGAN_NAME, resourceBean);

        if (!isIcfNameUidSame()) {
            shadowUuid = (String) identifier.getRealValue();
        }
        PrismAsserts.assertPropertyValue(repoShadow, SchemaConstants.ICFS_UID_PATH, shadowUuid);

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    /**
     * MID-4754
     */
    @Test
    public void test310ModifyMorganEnlistTimestamp() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(
                ShadowType.class,
                ACCOUNT_MORGAN_OID,
                DUMMY_ACCOUNT_ATTRIBUTE_ENLIST_TIMESTAMP_PATH,
                XmlTypeConverter.createXMLGregorianCalendarFromIso8601(ACCOUNT_MORGAN_PASSWORD_ENLIST_TIMESTAMP_MODIFIED));
        displayDumpable("ObjectDelta", delta);
        delta.checkConsistence();

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), null, task, result);

        then();
        assertSuccess(result);

        delta.checkConsistence();
        // check if attribute was changed
        DummyAccount dummyAccount = getDummyAccount(transformNameFromResource(ACCOUNT_CPTMORGAN_NAME), morganIcfUid);
        displayDumpable("Dummy account", dummyAccount);
        ZonedDateTime enlistTimestamp = dummyAccount.getAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_ENLIST_TIMESTAMP_NAME, ZonedDateTime.class);
        assertEqualTime("wrong dummy enlist timestamp", ACCOUNT_MORGAN_PASSWORD_ENLIST_TIMESTAMP_MODIFIED, enlistTimestamp);

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    /**
     * Change password, using runAsAccountOid option.
     * MID-4397
     */
    @Test
    public void test330ModifyAccountWillPasswordSelfService() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ObjectDelta<ShadowType> delta = createAccountPasswordDelta(ACCOUNT_WILL_OID, ACCOUNT_WILL_PASSWORD_321, ACCOUNT_WILL_PASSWORD_123);
        displayDumpable("ObjectDelta", delta);

        ProvisioningOperationOptions options = ProvisioningOperationOptions.createRunAsAccountOid(ACCOUNT_WILL_OID);

        when();
        provisioningService.modifyObject(ShadowType.class, delta.getOid(), delta.getModifications(),
                new OperationProvisioningScriptsType(), options, task, result);

        then();
        assertSuccess(result);

        // Check if the account was created in the dummy resource
        assertDummyAccount(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid)
                .assertPassword(ACCOUNT_WILL_PASSWORD_321)
                .assertLastModifier(getLastModifierName(ACCOUNT_WILL_USERNAME));

        accountWillCurrentPassword = ACCOUNT_WILL_PASSWORD_321;

        // Check if the shadow is in the repo
        PrismObject<ShadowType> repoShadow = getShadowRepo(ACCOUNT_WILL_OID);
        assertNotNull("Shadow was not created in the repository", repoShadow);
        display("Repository shadow", repoShadow);

        checkRepoAccountShadow(repoShadow);
        assertRepoShadowCredentials(repoShadow, ACCOUNT_WILL_PASSWORD_321);

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    private static final String WILL_GOSSIP_AVAST = "Aye! Avast!";
    private static final String WILL_GOSSIP_BLOOD_OF_A_PIRATE = "Blood of a pirate";
    private static final String WILL_GOSSIP_EUNUCH = "Eunuch!";

    /**
     * Gossip is a multivalue attribute. Make sure that replace operations work
     * as expected also for multivalue.
     */
    @Test
    public void test340ModifyWillReplaceGossipBloodAvast() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();
        // This turns on recording of the operations
        dummyResourceCtl.setSyncStyle(DummySyncStyle.DUMB);
        dummyResource.clearDeltas();

        List<ItemDelta<?, ?>> mods = getGossipDelta(PlusMinusZero.ZERO, WILL_GOSSIP_BLOOD_OF_A_PIRATE, WILL_GOSSIP_AVAST);

        when();
        provisioningService.modifyObject(ShadowType.class, ACCOUNT_WILL_OID, mods, null, null, task, result);

        then();
        assertSuccess(result);

        assertAccountWillGossip(WILL_GOSSIP_BLOOD_OF_A_PIRATE, WILL_GOSSIP_AVAST);
        assertWillDummyGossipRecord(PlusMinusZero.ZERO, WILL_GOSSIP_BLOOD_OF_A_PIRATE, WILL_GOSSIP_AVAST);

        syncServiceMock.assertSingleNotifySuccessOnly();
        assertSteadyResource();
    }

    /**
     * Gossip is a multivalue attribute. Make sure that replace operations work
     * as expected also for multivalue.
     */
    @Test
    public void test342ModifyWillAddGossipEunuch() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();
        // This turns on recording of the operations
        dummyResourceCtl.setSyncStyle(DummySyncStyle.DUMB);
        dummyResource.clearDeltas();

        List<ItemDelta<?, ?>> mods = getGossipDelta(PlusMinusZero.PLUS, WILL_GOSSIP_EUNUCH);

        when();
        provisioningService.modifyObject(ShadowType.class, ACCOUNT_WILL_OID, mods, null, null, task, result);

        then();
        assertSuccess(result);

        assertAccountWillGossip(WILL_GOSSIP_BLOOD_OF_A_PIRATE, WILL_GOSSIP_AVAST, WILL_GOSSIP_EUNUCH);
        assertWillDummyGossipRecord(PlusMinusZero.PLUS, WILL_GOSSIP_EUNUCH);

        syncServiceMock.assertSingleNotifySuccessOnly();
        assertSteadyResource();
    }

    /**
     * Gossip is a multivalue attribute. Make sure that replace operations work
     * as expected also for multivalue.
     */
    @Test
    public void test344ModifyWillDeleteGossipAvast() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();
        // This turns on recording of the operations
        dummyResourceCtl.setSyncStyle(DummySyncStyle.DUMB);
        dummyResource.clearDeltas();

        List<ItemDelta<?, ?>> mods = getGossipDelta(PlusMinusZero.MINUS, WILL_GOSSIP_AVAST);

        when();
        provisioningService.modifyObject(ShadowType.class, ACCOUNT_WILL_OID, mods, null, null, task, result);

        then();
        assertSuccess(result);

        assertAccountWillGossip(WILL_GOSSIP_BLOOD_OF_A_PIRATE, WILL_GOSSIP_EUNUCH);
        assertWillDummyGossipRecord(PlusMinusZero.MINUS, WILL_GOSSIP_AVAST);

        syncServiceMock.assertSingleNotifySuccessOnly();
        assertSteadyResource();
    }

    private List<ItemDelta<?, ?>> getGossipDelta(PlusMinusZero plusMinusZero, String... values) throws SchemaException {
        List<ItemDelta<?, ?>> mods = prismContext.deltaFor(ShadowType.class)
                .property(DUMMY_ACCOUNT_ATTRIBUTE_GOSSIP_PATH, null)
                .mod(plusMinusZero, (Object[]) values)
                .asItemDeltas();
        display("Modifications", mods);
        return mods;
    }

    private void assertAccountWillGossip(String... values) throws ConnectException, FileNotFoundException, SchemaViolationException, ConflictException, InterruptedException {
        displayDumpable("Account will", getDummyAccount(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid));
        //noinspection unchecked
        assertDummyAccount(transformNameFromResource(ACCOUNT_WILL_USERNAME), willIcfUid)
                .assertAttribute(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_GOSSIP_NAME, values);
    }

    protected void assertWillDummyGossipRecord(PlusMinusZero plusminus, String... expectedValues) {
        displayValue("Dummy resource deltas", dummyResource.dumpDeltas());
        List<DummyDelta> dummyDeltas = dummyResource.getDeltas();
        assertFalse("Empty dummy resource deltas", dummyDeltas.isEmpty());
        assertEquals("Too many dummy resource deltas", 1, dummyDeltas.size());
        DummyDelta dummyDelta = dummyDeltas.get(0);
        assertEquals("Wrong dummy resource delta object name", transformNameFromResource(ACCOUNT_WILL_USERNAME), dummyDelta.getObjectName());
        assertEquals("Wrong dummy resource delta type", DummyDeltaType.MODIFY, dummyDelta.getType());
        assertEquals("Wrong dummy resource delta attribute", DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_GOSSIP_NAME, dummyDelta.getAttributeName());
        Collection<Object> valuesToConsider;
        switch (plusminus) {
            case PLUS:
                valuesToConsider = dummyDelta.getValuesAdded();
                break;
            case MINUS:
                valuesToConsider = dummyDelta.getValuesDeleted();
                break;
            case ZERO:
                valuesToConsider = dummyDelta.getValuesReplaced();
                break;
            default:
                valuesToConsider = null;
        }
        PrismAsserts.assertEqualsCollectionUnordered("Wrong values for " + plusminus + " in dummy resource delta", valuesToConsider, expectedValues);
    }

    @SuppressWarnings("SameParameterValue")
    protected String getLastModifierName(String expected) {
        return transformNameToResource(expected);
    }

    // test4xx reserved for subclasses

    @Test
    public void test500AddProtectedAccount() throws Exception {
        testAddProtectedAccount(ACCOUNT_DAVIEJONES_USERNAME);
    }

    @Test
    public void test501GetProtectedAccountShadow() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // WHEN
        PrismObject<ShadowType> account = provisioningService.getObject(ShadowType.class, ACCOUNT_DAEMON_OID, null, task, result);

        assertEquals("" + account + " is not protected", Boolean.TRUE, account.asObjectable().isProtectedObject());
        checkUniqueness(account);

        if (areMarksSupported()) {
            // Check if effective mark is applied in repository
            PrismObject<ShadowType> repoAccount = repositoryService.getObject(ShadowType.class, ACCOUNT_DAEMON_OID, null, result);
            var effectiveMarks = repoAccount.asObjectable().getEffectiveMarkRef();
            assertTrue("Effective marks should not be empty", effectiveMarks.stream().anyMatch(r -> SystemObjectsType.MARK_PROTECTED.value().equals(r.getOid())));
        }

        assertSuccess(result);
        assertSteadyResource();
    }

    /**
     * Attribute modification should fail.
     */
    @Test
    public void test502ModifyProtectedAccountShadowAttributes() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        Collection<PropertyDelta<String>> modifications = new ArrayList<>(1);
        ResourceSchema resourceSchema = ResourceSchemaFactory.getRawSchemaRequired(resource.asObjectable());
        ResourceObjectClassDefinition defaultAccountDefinition =
                resourceSchema.findObjectClassDefinitionRequired(RI_ACCOUNT_OBJECT_CLASS);
        //noinspection unchecked
        ResourceAttributeDefinition<String> fullnameAttrDef =
                (ResourceAttributeDefinition<String>) defaultAccountDefinition.findAttributeDefinition("fullname");
        ResourceAttribute<String> fullnameAttr = fullnameAttrDef.instantiate();
        PropertyDelta<String> fullnameDelta = fullnameAttr.createDelta(ItemPath.create(ShadowType.F_ATTRIBUTES,
                fullnameAttrDef.getItemName()));
        fullnameDelta.setRealValuesToReplace("Good Daemon");
        modifications.add(fullnameDelta);

        // WHEN
        try {
            provisioningService.modifyObject(ShadowType.class, ACCOUNT_DAEMON_OID, modifications, null, null, task, result);
            AssertJUnit.fail("Expected security exception while modifying 'daemon' account");
        } catch (SecurityViolationException e) {
            displayExpectedException(e);
        }

        assertFailure(result);
        syncServiceMock.assertSingleNotifyFailureOnly();

//        checkConsistency();

        assertSteadyResource();
    }

    /**
     * Modification of non-attribute property should go OK.
     */
    @Test
    public void test503ModifyProtectedAccountShadowProperty() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        ObjectDelta<ShadowType> shadowDelta = prismContext.deltaFactory().object()
                .createModificationReplaceProperty(ShadowType.class, ACCOUNT_DAEMON_OID,
                        ShadowType.F_SYNCHRONIZATION_SITUATION, SynchronizationSituationType.DISPUTED);

        // WHEN
        provisioningService.modifyObject(ShadowType.class, ACCOUNT_DAEMON_OID, shadowDelta.getModifications(), null, null, task, result);

        // THEN
        assertSuccess(result);

        syncServiceMock.assertSingleNotifySuccessOnly();

        PrismObject<ShadowType> shadowAfter = provisioningService.getObject(ShadowType.class, ACCOUNT_DAEMON_OID, null, task, result);
        assertEquals("Wrong situation", SynchronizationSituationType.DISPUTED, shadowAfter.asObjectable().getSynchronizationSituation());

        assertSteadyResource();
    }

    @Test
    public void test509DeleteProtectedAccountShadow() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        try {
            when();

            provisioningService.deleteObject(ShadowType.class, ACCOUNT_DAEMON_OID, null, null, task, result);

            AssertJUnit.fail("Expected security exception while deleting 'daemon' account");
        } catch (SecurityViolationException e) {
            then();
            displayExpectedException(e);
        }

        assertFailure(result);

        syncServiceMock.assertSingleNotifyFailureOnly();

//        checkConsistency();

        assertSteadyResource();
    }

    @Test
    public void test510AddProtectedAccounts() throws Exception {
        // GIVEN
        testAddProtectedAccount("Xavier");
        testAddProtectedAccount("Xenophobia");
        testAddProtectedAccount("nobody-adm");
        testAddAccount("abcadm");
        testAddAccount("piXel");
        testAddAccount("supernaturalius");
    }

    @Test
    public void test511AddProtectedAccountCaseIgnore() throws Exception {
        // GIVEN
        testAddAccount("xaxa");
        testAddAccount("somebody-ADM");
    }

    private PrismObject<ShadowType> createAccountShadow(String username) throws SchemaException {
        ResourceSchema resourceSchema = requireNonNull(ResourceSchemaFactory.getRawSchema(resource));
        ResourceObjectClassDefinition defaultAccountDefinition =
                resourceSchema.findObjectClassDefinitionRequired(RI_ACCOUNT_OBJECT_CLASS);
        ShadowType shadowType = new ShadowType();
        shadowType.setName(PrismTestUtil.createPolyStringType(username));
        ObjectReferenceType resourceRef = new ObjectReferenceType();
        resourceRef.setOid(resource.getOid());
        shadowType.setResourceRef(resourceRef);
        shadowType.setObjectClass(defaultAccountDefinition.getTypeName());
        PrismObject<ShadowType> shadow = shadowType.asPrismObject();
        PrismContainer<Containerable> attrsCont = shadow.findOrCreateContainer(ShadowType.F_ATTRIBUTES);
        PrismProperty<String> icfsNameProp = attrsCont.findOrCreateProperty(SchemaConstants.ICFS_NAME);
        icfsNameProp.setRealValue(username);
        return shadow;
    }

    protected void testAddProtectedAccount(String username) throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        PrismObject<ShadowType> shadow = createAccountShadow(username);

        // WHEN
        try {
            provisioningService.addObject(shadow, null, null, task, result);
            AssertJUnit.fail("Expected security exception while adding '" + username + "' account");
        } catch (SecurityViolationException e) {
            displayExpectedException(e);
        }

        assertFailure(result);

        syncServiceMock.assertSingleNotifyFailureOnly();

        assertSteadyResource();
    }

    private void testAddAccount(String username) throws Exception {
        OperationResult result = createSubresult("addAccount");
        syncServiceMock.reset();

        PrismObject<ShadowType> shadow = createAccountShadow(username);

        // WHEN
        provisioningService.addObject(shadow, null, null, getTestTask(), result);

        assertSuccess(result);

        syncServiceMock.assertSingleNotifySuccessOnly();

        assertSteadyResource();
    }

    /**
     * Make sure that refresh of the shadow adds missing primaryIdentifierValue
     * to the shadow.
     * This is a test for migration from previous midPoint versions that haven't
     * had primaryIdentifierValue.
     */
    @Test
    public void test520MigrationPrimaryIdentifierValueRefresh() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        PrismObject<ShadowType> shadowBefore = PrismTestUtil.parseObject(ACCOUNT_RELIC_FILE);
        repositoryService.addObject(shadowBefore, null, result);

        when();

        provisioningService.refreshShadow(shadowBefore, null, task, result);

        then();
        assertSuccess(result);

        assertRepoShadow(ACCOUNT_RELIC_OID)
                .assertName(ACCOUNT_RELIC_USERNAME)
                .assertPrimaryIdentifierValue(ACCOUNT_RELIC_USERNAME);

        assertSteadyResource();
    }

    /**
     * Test for proper handling of "already exists" exception. We try to add a shadow.
     * It fails, because there is unknown conflicting object on the resource. But a new
     * shadow for the conflicting object should be created in the repository.
     * MID-3603
     */
    @Test
    public void test600AddAccountAlreadyExist() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        syncServiceMock.reset();

        dummyResourceCtl.addAccount(ACCOUNT_MURRAY_USERNAME, ACCOUNT_MURRAY_USERNAME);

        PrismObject<ShadowType> account = createShadowNameOnly(resource, ACCOUNT_MURRAY_USERNAME);
        account.checkConsistence();

        display("Adding shadow", account);

        when();
        try {
            provisioningService.addObject(account, null, null, task, result);

            assertNotReached();
        } catch (ObjectAlreadyExistsException e) {
            then();
            displayExpectedException(e);
        }

        assertFailure(result);

        syncServiceMock.assertNotifyChange();

        // Even though the operation failed a shadow should be created for the conflicting object
        PrismObject<ShadowType> accountRepo = findAccountShadowByUsername(getMurrayRepoIcfName(), resource, result);
        assertNotNull("Shadow for conflicting object was not created in the repository", accountRepo);
        display("Repository shadow", accountRepo);
        checkRepoAccountShadow(accountRepo);

        assertEquals("Wrong ICF NAME in murray (repo) shadow", getMurrayRepoIcfName(), getIcfName(accountRepo));

        assertSteadyResource();
    }

    private final LiveSyncTokenStorage tokenStorage = new DummyTokenStorageImpl();

    @Test
    public void test800LiveSyncInit() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        dummyResource.setSyncStyle(DummySyncStyle.DUMB);
        dummyResource.clearDeltas();
        syncServiceMock.reset();

        // Dry run to remember the current sync token in the task instance.
        // Otherwise a last sync token would be used and
        // no change would be detected
        ResourceOperationCoordinates coords = getDefaultAccountObjectClassCoordinates();

        when();
        mockLiveSyncTaskHandler.synchronize(coords, tokenStorage, task, result);

        then();
        assertSuccess(result);

        // No change, no fun
        syncServiceMock.assertNoNotifyChange();

        checkAllShadows();

        assertSteadyResource();
    }

    private @NotNull ResourceOperationCoordinates getDefaultAccountObjectClassCoordinates() {
        return ResourceOperationCoordinates.ofObjectClass(
                RESOURCE_DUMMY_OID,
                getDefaultAccountObjectClass(resourceBean));
    }

    @Test
    public void test801LiveSyncAddBlackbeard() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();
        dummyResource.setSyncStyle(DummySyncStyle.DUMB);
        DummyAccount newAccount = new DummyAccount(BLACKBEARD_USERNAME);
        newAccount.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Edward Teach");
        newAccount.addAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOOT_NAME, 66666);
        newAccount.setEnabled(true);
        newAccount.setPassword("shiverMEtimbers");
        dummyResource.addAccount(newAccount);
        blackbeardIcfUid = newAccount.getId();

        displayValue("Resource before sync", dummyResource.debugDump());

        ResourceOperationCoordinates coords = getDefaultAccountObjectClassCoordinates();

        when();
        mockLiveSyncTaskHandler.synchronize(coords, tokenStorage, task, result);

        then();
        assertSuccess("Synchronization result is not OK", result);

        syncServiceMock.assertNotifyChange();

        ResourceObjectShadowChangeDescription lastChange = syncServiceMock.getLastChange();
        displayDumpable("The change", lastChange);

        assertNull("Delta present when not expecting it", lastChange.getObjectDelta());
        PrismObject<ShadowType> currentShadow = lastChange.getShadowedResourceObject();
        assertNotNull("Current shadow missing", lastChange.getShadowedResourceObject());
        assertTrue("Wrong type of current shadow: " + currentShadow.getClass().getName(),
                currentShadow.canRepresent(ShadowType.class));

        ResourceAttributeContainer attributesContainer = ShadowUtil
                .getAttributesContainer(currentShadow);
        assertNotNull("No attributes container in current shadow", attributesContainer);
        Collection<ResourceAttribute<?>> attributes = attributesContainer.getAttributes();
        assertFalse("Attributes container is empty", attributes.isEmpty());
        assertAttribute(currentShadow,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Edward Teach");
        assertAttribute(currentShadow,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOOT_NAME, 66666);
        assertEquals("Unexpected number of attributes", 4, attributes.size());

        PrismObject<ShadowType> accountRepo = findAccountShadowByUsername(getBlackbeardRepoIcfName(), resource, result);
        assertNotNull("Shadow was not created in the repository", accountRepo);
        display("Repository shadow", accountRepo);
        checkRepoAccountShadow(accountRepo);

        checkAllShadows();

        assertSteadyResource();
    }

    @Test
    public void test802LiveSyncModifyBlackbeard() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();

        DummyAccount dummyAccount = getDummyAccountAssert(BLACKBEARD_USERNAME, blackbeardIcfUid);
        dummyAccount.replaceAttributeValue("fullname", "Captain Blackbeard");

        displayValue("Resource before sync", dummyResource.debugDump());

        ResourceOperationCoordinates coords = getDefaultAccountObjectClassCoordinates();

        when();
        mockLiveSyncTaskHandler.synchronize(coords, tokenStorage, task, result);

        then();
        assertSuccess("Synchronization result is not OK", result);

        syncServiceMock.assertNotifyChange();

        ResourceObjectShadowChangeDescription lastChange = syncServiceMock.getLastChange();
        displayDumpable("The change", lastChange);

        assertNull("Delta present when not expecting it", lastChange.getObjectDelta());
        PrismObject<ShadowType> currentShadow = lastChange.getShadowedResourceObject();
        assertNotNull("Current shadow missing", lastChange.getShadowedResourceObject());
        assertTrue("Wrong type of current shadow: " + currentShadow.getClass().getName(),
                currentShadow.canRepresent(ShadowType.class));

        ResourceAttributeContainer attributesContainer = ShadowUtil
                .getAttributesContainer(currentShadow);
        assertNotNull("No attributes container in current shadow", attributesContainer);
        Collection<ResourceAttribute<?>> attributes = attributesContainer.getAttributes();
        assertFalse("Attributes container is empty", attributes.isEmpty());
        assertAttribute(currentShadow,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Captain Blackbeard");
        assertAttribute(currentShadow,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOOT_NAME, 66666);
        assertEquals("Unexpected number of attributes", 4, attributes.size());

        PrismObject<ShadowType> accountRepo = findAccountShadowByUsername(getBlackbeardRepoIcfName(), resource, result);
        assertNotNull("Shadow was not created in the repository", accountRepo);
        display("Repository shadow", accountRepo);
        checkRepoAccountShadow(accountRepo);

        checkAllShadows();

        assertSteadyResource();
    }

    @Test
    public void test810LiveSyncAddDrakeDumbObjectClass() throws Exception {
        testLiveSyncAddDrake(DummySyncStyle.DUMB, getDefaultAccountObjectClass(resourceBean));
    }

    @Test
    public void test812LiveSyncModifyDrakeDumbObjectClass() throws Exception {
        testLiveSyncModifyDrake(DummySyncStyle.DUMB, getDefaultAccountObjectClass(resourceBean));
    }

    @Test
    public void test815LiveSyncAddCorsairsDumbObjectClass() throws Exception {
        testLiveSyncAddCorsairs(DummySyncStyle.DUMB, getDefaultAccountObjectClass(resourceBean), false);
    }

    @Test
    public void test817LiveSyncDeleteCorsairsDumbObjectClass() throws Exception {
        testLiveSyncDeleteCorsairs(DummySyncStyle.DUMB, getDefaultAccountObjectClass(resourceBean), false);
    }

    @Test
    public void test819LiveSyncDeleteDrakeDumbObjectClass() throws Exception {
        testLiveSyncDeleteDrake(DummySyncStyle.DUMB, getDefaultAccountObjectClass(resourceBean));
    }

    @Test
    public void test820LiveSyncAddDrakeSmartObjectClass() throws Exception {
        testLiveSyncAddDrake(DummySyncStyle.SMART, getDefaultAccountObjectClass(resourceBean));
    }

    @Test
    public void test822LiveSyncModifyDrakeSmartObjectClass() throws Exception {
        testLiveSyncModifyDrake(DummySyncStyle.SMART, getDefaultAccountObjectClass(resourceBean));
    }

    @Test
    public void test825LiveSyncAddCorsairsSmartObjectClass() throws Exception {
        testLiveSyncAddCorsairs(DummySyncStyle.SMART, getDefaultAccountObjectClass(resourceBean), false);
    }

    @Test
    public void test827LiveSyncDeleteCorsairsSmartObjectClass() throws Exception {
        testLiveSyncDeleteCorsairs(DummySyncStyle.SMART, getDefaultAccountObjectClass(resourceBean), false);
    }

    @Test
    public void test829LiveSyncDeleteDrakeSmartObjectClass() throws Exception {
        testLiveSyncDeleteDrake(DummySyncStyle.SMART, getDefaultAccountObjectClass(resourceBean));
    }

    @Test
    public void test830LiveSyncAddDrakeDumbAny() throws Exception {
        testLiveSyncAddDrake(DummySyncStyle.DUMB, null);
    }

    @Test
    public void test832LiveSyncModifyDrakeDumbAny() throws Exception {
        testLiveSyncModifyDrake(DummySyncStyle.DUMB, null);
    }

    @Test
    public void test835LiveSyncAddCorsairsDumbAny() throws Exception {
        testLiveSyncAddCorsairs(DummySyncStyle.DUMB, null, true);
    }

    @Test
    public void test837LiveSyncDeleteCorsairsDumbAny() throws Exception {
        testLiveSyncDeleteCorsairs(DummySyncStyle.DUMB, null, true);
    }

    @Test
    public void test839LiveSyncDeleteDrakeDumbAny() throws Exception {
        testLiveSyncDeleteDrake(DummySyncStyle.DUMB, null);
    }

    @Test
    public void test840LiveSyncAddDrakeSmartAny() throws Exception {
        testLiveSyncAddDrake(DummySyncStyle.SMART, null);
    }

    @Test
    public void test842LiveSyncModifyDrakeSmartAny() throws Exception {
        testLiveSyncModifyDrake(DummySyncStyle.SMART, null);
    }

    @Test
    public void test845LiveSyncAddCorsairsSmartAny() throws Exception {
        testLiveSyncAddCorsairs(DummySyncStyle.SMART, null, true);
    }

    @Test
    public void test847LiveSyncDeleteCorsairsSmartAny() throws Exception {
        testLiveSyncDeleteCorsairs(DummySyncStyle.SMART, null, true);
    }

    @Test
    public void test849LiveSyncDeleteDrakeSmartAny() throws Exception {
        testLiveSyncDeleteDrake(DummySyncStyle.SMART, null);
    }

    private void testLiveSyncAddDrake(DummySyncStyle syncStyle, QName objectClass) throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();
        dummyResource.setSyncStyle(syncStyle);
        DummyAccount newAccount = new DummyAccount(DRAKE_USERNAME);
        newAccount.addAttributeValues("fullname", "Sir Francis Drake");
        newAccount.setEnabled(true);
        newAccount.setPassword("avast!");
        dummyResource.addAccount(newAccount);
        drakeIcfUid = newAccount.getId();

        displayValue("Resource before sync", dummyResource.debugDump());

        ResourceOperationCoordinates coords = ResourceOperationCoordinates.of(RESOURCE_DUMMY_OID, objectClass);

        when();
        mockLiveSyncTaskHandler.synchronize(coords, tokenStorage, task, result);

        then();
        assertSuccess("Synchronization result is not OK", result);

        syncServiceMock.assertNotifyChange();

        ResourceObjectShadowChangeDescription lastChange = syncServiceMock.getLastChange();
        displayDumpable("The change", lastChange);

        if (syncStyle == DummySyncStyle.DUMB) {
            assertNull("Delta present when not expecting it", lastChange.getObjectDelta());
        } else {
            ObjectDelta<? extends ShadowType> objectDelta = lastChange.getObjectDelta();
            assertNotNull("Delta present when not expecting it", objectDelta);
            assertTrue("Delta is not add: " + objectDelta, objectDelta.isAdd());
        }

        ShadowType currentShadowType = lastChange.getShadowedResourceObject().asObjectable();
        assertNotNull("Current shadow missing", lastChange.getShadowedResourceObject());
        PrismAsserts.assertClass("current shadow", ShadowType.class, currentShadowType);

        ResourceAttributeContainer attributesContainer = ShadowUtil
                .getAttributesContainer(currentShadowType);
        assertNotNull("No attributes container in current shadow", attributesContainer);
        Collection<ResourceAttribute<?>> attributes = attributesContainer.getAttributes();
        assertFalse("Attributes container is empty", attributes.isEmpty());
        assertEquals("Unexpected number of attributes", 3, attributes.size());
        ResourceAttribute<?> fullnameAttribute = attributesContainer.findAttribute(new QName(MidPointConstants.NS_RI, "fullname"));
        assertNotNull("No fullname attribute in current shadow", fullnameAttribute);
        assertEquals("Wrong value of fullname attribute in current shadow", "Sir Francis Drake",
                fullnameAttribute.getRealValue());

        drakeAccountOid = currentShadowType.getOid();
        PrismObject<ShadowType> repoShadow = getShadowRepo(drakeAccountOid);
        display("Drake repo shadow", repoShadow);

        PrismObject<ShadowType> accountRepo = findAccountShadowByUsername(getDrakeRepoIcfName(), resource, result);
        assertNotNull("Shadow was not created in the repository", accountRepo);
        display("Repository shadow", accountRepo);
        checkRepoAccountShadow(accountRepo);

        checkAllShadows();

        assertSteadyResource();
    }

    private void testLiveSyncModifyDrake(DummySyncStyle syncStyle, QName objectClass) throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();
        dummyResource.setSyncStyle(syncStyle);

        DummyAccount dummyAccount = getDummyAccountAssert(DRAKE_USERNAME, drakeIcfUid);
        dummyAccount.replaceAttributeValue("fullname", "Captain Drake");

        ResourceOperationCoordinates coords = ResourceOperationCoordinates.of(RESOURCE_DUMMY_OID, objectClass);

        when();
        mockLiveSyncTaskHandler.synchronize(coords, tokenStorage, task, result);

        then();
        result.computeStatus();
        display("Synchronization result", result);
        TestUtil.assertSuccess("Synchronization result is not OK", result);

        syncServiceMock.assertNotifyChange();

        ResourceObjectShadowChangeDescription lastChange = syncServiceMock.getLastChange();
        displayDumpable("The change", lastChange);

        assertNull("Delta present when not expecting it", lastChange.getObjectDelta());
        PrismObject<ShadowType> currentShadow = lastChange.getShadowedResourceObject();
        assertNotNull("Current shadow missing", lastChange.getShadowedResourceObject());
        assertTrue("Wrong type of current shadow: " + currentShadow.getClass().getName(),
                currentShadow.canRepresent(ShadowType.class));

        ResourceAttributeContainer attributesContainer = ShadowUtil
                .getAttributesContainer(currentShadow);
        assertNotNull("No attributes container in current shadow", attributesContainer);
        Collection<ResourceAttribute<?>> attributes = attributesContainer.getAttributes();
        assertFalse("Attributes container is empty", attributes.isEmpty());
        assertAttribute(currentShadow,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Captain Drake");
        assertEquals("Unexpected number of attributes", 3, attributes.size());

        PrismObject<ShadowType> accountRepo = findAccountShadowByUsername(getDrakeRepoIcfName(), resource, result);
        assertNotNull("Shadow was not created in the repository", accountRepo);
        display("Repository shadow", accountRepo);
        checkRepoAccountShadow(accountRepo);

        checkAllShadows();

        assertSteadyResource();
    }

    private void testLiveSyncAddCorsairs(
            DummySyncStyle syncStyle, QName objectClass, boolean expectReaction) throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();
        dummyResource.setSyncStyle(syncStyle);
        DummyGroup newGroup = new DummyGroup(GROUP_CORSAIRS_NAME);
        newGroup.setEnabled(true);
        dummyResource.addGroup(newGroup);
        corsairsIcfUid = newGroup.getId();

        displayValue("Resource before sync", dummyResource.debugDump());

        ResourceOperationCoordinates coords = ResourceOperationCoordinates.of(RESOURCE_DUMMY_OID, objectClass);

        when();
        mockLiveSyncTaskHandler.synchronize(coords, tokenStorage, task, result);

        then();
        assertSuccess("Synchronization result is not OK", result);

        if (expectReaction) {

            syncServiceMock.assertNotifyChange();

            ResourceObjectShadowChangeDescription lastChange = syncServiceMock.getLastChange();
            displayDumpable("The change", lastChange);

            if (syncStyle == DummySyncStyle.DUMB) {
                assertNull("Delta present when not expecting it", lastChange.getObjectDelta());
            } else {
                ObjectDelta<? extends ShadowType> objectDelta = lastChange.getObjectDelta();
                assertNotNull("Delta present when not expecting it", objectDelta);
                assertTrue("Delta is not add: " + objectDelta, objectDelta.isAdd());
            }

            ShadowType currentShadowType = lastChange.getShadowedResourceObject().asObjectable();
            assertNotNull("Current shadow missing", lastChange.getShadowedResourceObject());
            PrismAsserts.assertClass("current shadow", ShadowType.class, currentShadowType);

            ResourceAttributeContainer attributesContainer = ShadowUtil
                    .getAttributesContainer(currentShadowType);
            assertNotNull("No attributes container in current shadow", attributesContainer);
            Collection<ResourceAttribute<?>> attributes = attributesContainer.getAttributes();
            assertFalse("Attributes container is empty", attributes.isEmpty());
            assertEquals("Unexpected number of attributes", 2, attributes.size());

            corsairsShadowOid = currentShadowType.getOid();
            PrismObject<ShadowType> repoShadow = getShadowRepo(corsairsShadowOid);
            display("Corsairs repo shadow", repoShadow);

            PrismObject<ShadowType> accountRepo = findShadowByName(new QName(RESOURCE_DUMMY_NS, SchemaConstants.GROUP_OBJECT_CLASS_LOCAL_NAME), GROUP_CORSAIRS_NAME, resource, result);
            assertNotNull("Shadow was not created in the repository", accountRepo);
            display("Repository shadow", accountRepo);
            ProvisioningTestUtil.checkRepoShadow(repoShadow, ShadowKindType.ENTITLEMENT);

        } else {
            syncServiceMock.assertNoNotifyChange();
        }

        checkAllShadows();

        assertSteadyResource();
    }

    private void testLiveSyncDeleteCorsairs(
            DummySyncStyle syncStyle, QName objectClass, boolean expectReaction) throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();
        dummyResource.setSyncStyle(syncStyle);
        if (isNameUnique()) {
            dummyResource.deleteGroupByName(GROUP_CORSAIRS_NAME);
        } else {
            dummyResource.deleteGroupById(corsairsIcfUid);
        }

        displayValue("Resource before sync", dummyResource.debugDump());

        ResourceOperationCoordinates coords = ResourceOperationCoordinates.of(RESOURCE_DUMMY_OID, objectClass);

        when();
        mockLiveSyncTaskHandler.synchronize(coords, tokenStorage, task, result);

        then();
        assertSuccess("Synchronization result is not OK", result);

        if (expectReaction) {

            syncServiceMock.assertNotifyChange();

            syncServiceMock
                    .lastNotifyChange()
                    .display()
                    .delta()
                        .assertChangeType(ChangeType.DELETE)
                        .assertObjectTypeClass(ShadowType.class)
                        .end()
                    .currentShadow()
                        .assertOid(corsairsShadowOid)
                        .assertTombstone()
                        .attributes()
                            .assertAttributes(SchemaConstants.ICFS_NAME, SchemaConstants.ICFS_UID)
                            .assertValue(SchemaConstants.ICFS_NAME, GROUP_CORSAIRS_NAME)
                            .end()
                        .end();

            assertRepoShadow(corsairsShadowOid)
                    .assertTombstone();

            // Clean slate for next tests
            repositoryService.deleteObject(ShadowType.class, corsairsShadowOid, result);

        } else {
            syncServiceMock.assertNoNotifyChange();
        }

        checkAllShadows();

        assertSteadyResource();
    }

    private void testLiveSyncDeleteDrake(
            DummySyncStyle syncStyle, QName objectClass) throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        syncServiceMock.reset();
        dummyResource.setSyncStyle(syncStyle);
        if (isNameUnique()) {
            dummyResource.deleteAccountByName(DRAKE_USERNAME);
        } else {
            dummyResource.deleteAccountById(drakeIcfUid);
        }

        displayValue("Resource before sync", dummyResource.debugDump());

        ResourceOperationCoordinates coords = ResourceOperationCoordinates.of(RESOURCE_DUMMY_OID, objectClass);

        when();
        mockLiveSyncTaskHandler.synchronize(coords, tokenStorage, task, result);

        then();
        assertSuccess("Synchronization result is not OK", result);

        syncServiceMock.assertNotifyChange();

        ResourceObjectShadowChangeDescription lastChange = syncServiceMock.getLastChange();
        displayDumpable("The change", lastChange);

        syncServiceMock
                .lastNotifyChange()
                .delta()
                    .assertChangeType(ChangeType.DELETE)
                    .assertObjectTypeClass(ShadowType.class)
                .end()
                .currentShadow()
                    .assertOid(drakeAccountOid)
                    .assertTombstone();

        assertRepoShadow(drakeAccountOid)
                .assertTombstone();

        checkAllShadows();

        // Clean slate for next tests
        repositoryService.deleteObject(ShadowType.class, drakeAccountOid, result);

        assertSteadyResource();
    }

    @Test
    public void test890LiveSyncModifyProtectedAccount() throws Exception {
        // GIVEN
        Task syncTask = getTestTask();
        OperationResult result = syncTask.getResult();

        syncServiceMock.reset();

        DummyAccount dummyAccount = getDummyAccountAssert(ACCOUNT_DAEMON_USERNAME, daemonIcfUid);
        dummyAccount.replaceAttributeValue("fullname", "Maxwell deamon");

        ResourceOperationCoordinates coords = getDefaultAccountObjectClassCoordinates();

        when();
        mockLiveSyncTaskHandler.synchronize(coords, tokenStorage, syncTask, result);

        then();
        assertSuccess(result);

        ResourceObjectShadowChangeDescription lastChange = syncServiceMock.getLastChange();
        displayDumpable("The change", lastChange);

        syncServiceMock.assertNoNotifyChange();

        checkAllShadows();

        assertSteadyResource();
    }

    @Test
    public void test901FailResourceNotFound() throws Exception {
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        try {
            when("getting non-existent resource");
            PrismObject<ResourceType> object =
                    provisioningService.getObject(ResourceType.class, NOT_PRESENT_OID, null, task, result);
            AssertJUnit.fail(
                    "Expected ObjectNotFoundException to be thrown, but getObject returned " + object + " instead");
        } catch (ObjectNotFoundException e) {
            displayExpectedException(e);
            assertThat(e.getType()).as("missing object type").isEqualTo(ResourceType.class);
            assertThat(e.getOid()).as("missing object OID").isEqualTo(NOT_PRESENT_OID);
        }
        assertFailure(result);
    }

    /**
     * Getting shadow in maintenance mode should indicate `PARTIAL_ERROR`.
     */
    @Test
    public void test910GetShadowInMaintenance() throws Exception {
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        given("an account exists");
        var oid = provisioningService.addObject(
                createAccountShadow("test910"), null, null, task, result);

        and("resource is put into maintenance mode");
        turnMaintenanceModeOn(RESOURCE_DUMMY_OID, result);

        when("the shadow is read");
        var shadow = provisioningService.getObject(ShadowType.class, oid, null, task, result);

        then("shadow fetch result is set");
        assertShadowAfter(shadow)
                .assertFetchResult(OperationResultStatusType.PARTIAL_ERROR, "maintenance");

        and("the whole operation ends in PARTIAL_ERROR");
        assertPartialError(result);
    }

    /**
     * Creating shadow in maintenance mode should create a pending operation, and indicate `IN_PROGRESS`.
     */
    @Test
    public void test915AddShadowInMaintenance() throws Exception {
        Task task = getTestTask();
        OperationResult result = createOperationResult();

        given("resource is in maintenance mode");
        turnMaintenanceModeOn(RESOURCE_DUMMY_OID, result);

        when("the shadow is added");
        var oid = provisioningService.addObject(
                createAccountShadow("test915"), null, null, task, result);

        then("the result status is IN_PROGRESS");
        assertInProgress(result);

        and("a pending operation is recorded");
        PrismObject<ShadowType> shadow =
                provisioningService.getObject(ShadowType.class, oid, createNoFetchCollection(), task, result);

        // @formatter:off
        assertShadowAfter(shadow)
                .pendingOperations()
                    .singleOperation()
                        .assertType(PendingOperationTypeType.RETRY)
                        .assertExecutionStatus(PendingOperationExecutionStatusType.EXECUTING)
                        .assertResultStatus(OperationResultStatusType.IN_PROGRESS);
        // @formatter:on

        when("the shadow is added the second time");
        OperationResult result2 = createOperationResult();
        var oid2 = provisioningService.addObject(
                createAccountShadow("test915"), null, null, task, result2);

        then("the result status is IN_PROGRESS (account was still not created)");
        assertInProgress(result2);

        and("OID is the same");
        assertThat(oid2).as("OID after second 'add' attempt").isEqualTo(oid);

        and("there should be a pending operation (still)");
        PrismObject<ShadowType> shadow2 =
                provisioningService.getObject(ShadowType.class, oid, createNoFetchCollection(), task, result);

        // @formatter:off
        assertShadowAfter(shadow2)
                .pendingOperations()
                    .singleOperation()
                        .assertType(PendingOperationTypeType.RETRY)
                        .assertExecutionStatus(PendingOperationExecutionStatusType.EXECUTING)
                        .assertResultStatus(OperationResultStatusType.IN_PROGRESS);
        // @formatter:on
    }

    /**
     * Adds an association value (group membership) + attribute value during maintenance mode.
     * Checks that the future shadow has proper definitions for both - MID-8327.
     */
    @Test
    public void test920EntitleInMaintenance() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        given("resource is operational");
        turnMaintenanceModeOff(RESOURCE_DUMMY_OID, result);

        and("a group of 'pirates' is (again) there");
        provisioningService.addObject(
                prismContext.parseObject(GROUP_PIRATES_FILE),
                null, null, task, result);

        and("an account is there");
        String shadowOid = provisioningService.addObject(
                createAccountShadow("test920"), null, null, task, result);

        when("resource is in maintenance");
        turnMaintenanceModeOn(RESOURCE_DUMMY_OID, result);

        and("the account is entitled (pirates) + title changed (Cpt.)");
        ObjectDelta<ShadowType> delta = createEntitleDelta(shadowOid, DUMMY_ENTITLEMENT_GROUP_QNAME, GROUP_PIRATES_OID);
        delta.addModification(
                Resource.of(resource).deltaFor(RI_ACCOUNT_OBJECT_CLASS)
                        .item(DUMMY_ACCOUNT_ATTRIBUTE_TITLE_PATH).replace("Cpt.")
                        .asItemDelta());
        displayDumpable("Delta", delta);

        provisioningService.modifyObject(
                ShadowType.class, shadowOid, delta.getModifications(), null, null, task, result);

        and("maintenance mode is turned off");
        turnMaintenanceModeOff(RESOURCE_DUMMY_OID, result);

        and("object is get (now in regular mode)");
        var options = GetOperationOptionsBuilder.create()
                .futurePointInTime()
                .build();
        PrismObject<ShadowType> accountAfter = provisioningService.getObject(ShadowType.class, shadowOid, options, task, result);

        then("operation is pending (because of the retry interval)");
        // @formatter:off
        assertShadowAfter(accountAfter)
                .pendingOperations()
                    .singleOperation()
                        .assertType(PendingOperationTypeType.RETRY)
                        .assertExecutionStatus(PendingOperationExecutionStatusType.EXECUTING)
                        .assertResultStatus(OperationResultStatusType.IN_PROGRESS)
                        .delta()
                            .assertModify();
        // @formatter:on

        and("the association has correct definition");
        List<ShadowAssociationType> associationBeans = accountAfter.asObjectable().getAssociation();
        assertThat(associationBeans).as("association beans").hasSize(1);
        PrismContainerDefinition<?> identifiersDefinition =
                associationBeans.get(0).getIdentifiers().asPrismContainerValue().getParent().getDefinition();
        assertThat(identifiersDefinition)
                .as("definition of identifiers")
                .isInstanceOf(ResourceAttributeContainerDefinition.class);

        and("the title has correct definition");
        PrismPropertyDefinition<Object> titleDefinition =
                accountAfter.findProperty(DUMMY_ACCOUNT_ATTRIBUTE_TITLE_PATH).getDefinition();
        assertThat(titleDefinition)
                .as("definition of title")
                .isInstanceOf(ResourceAttributeDefinition.class);
    }

    // test999 shutdown in the superclass

    @SuppressWarnings("SameParameterValue")
    protected void checkCachedAccountShadow(
            PrismObject<ShadowType> shadowType,
            OperationResult parentResult,
            boolean fullShadow,
            XMLGregorianCalendar startTs,
            XMLGregorianCalendar endTs) throws SchemaException, ConfigurationException {
        checkAccountShadow(shadowType, parentResult, fullShadow);
    }

    private void checkGroupShadow(PrismObject<ShadowType> shadow, OperationResult parentResult)
            throws SchemaException, ConfigurationException {
        checkEntitlementShadow(shadow, parentResult, SchemaConstants.GROUP_OBJECT_CLASS_LOCAL_NAME, true);
    }

    @SuppressWarnings("SameParameterValue")
    private void checkGroupShadow(PrismObject<ShadowType> shadow, OperationResult parentResult, boolean fullShadow)
            throws SchemaException, ConfigurationException {
        checkEntitlementShadow(shadow, parentResult, SchemaConstants.GROUP_OBJECT_CLASS_LOCAL_NAME, fullShadow);
    }

    private void checkEntitlementShadow(
            PrismObject<ShadowType> shadow, OperationResult parentResult, String objectClassLocalName, boolean fullShadow)
            throws SchemaException, ConfigurationException {
        ObjectChecker<ShadowType> checker = createShadowChecker(fullShadow);
        ShadowUtil.checkConsistence(shadow, parentResult.getOperation());
        IntegrationTestTools.checkEntitlementShadow(
                shadow.asObjectable(),
                resourceBean,
                repositoryService,
                checker,
                objectClassLocalName,
                getUidMatchingRule(),
                prismContext,
                parentResult);
    }

    @SuppressWarnings("ConstantConditions")
    private void checkAllShadows() throws SchemaException, ConfigurationException {
        ObjectChecker<ShadowType> checker = null;
        IntegrationTestTools.checkAllShadows(resourceBean, repositoryService, checker, prismContext);
    }

    protected void checkRepoEntitlementShadow(PrismObject<ShadowType> repoShadow) {
        ProvisioningTestUtil.checkRepoEntitlementShadow(repoShadow);
    }

    protected void assertSyncOldShadow(PrismObject<? extends ShadowType> oldShadow, String repoName) {
        assertSyncOldShadow(oldShadow, repoName, 2);
    }

    void assertSyncOldShadow(PrismObject<? extends ShadowType> oldShadow, String repoName, Integer expectedNumberOfAttributes) {
        assertNotNull("Old shadow missing", oldShadow);
        assertNotNull("Old shadow does not have an OID", oldShadow.getOid());
        PrismAsserts.assertClass("old shadow", ShadowType.class, oldShadow);
        ShadowType oldShadowType = oldShadow.asObjectable();
        ResourceAttributeContainer attributesContainer = ShadowUtil
                .getAttributesContainer(oldShadowType);
        assertNotNull("No attributes container in old shadow", attributesContainer);
        Collection<ResourceAttribute<?>> attributes = attributesContainer.getAttributes();
        assertFalse("Attributes container is empty", attributes.isEmpty());
        if (expectedNumberOfAttributes != null) {
            assertEquals("Unexpected number of attributes", (int) expectedNumberOfAttributes, attributes.size());
        }
        ResourceAttribute<?> icfsNameAttribute = attributesContainer.findAttribute(SchemaConstants.ICFS_NAME);
        assertNotNull("No ICF name attribute in old  shadow", icfsNameAttribute);
        assertEquals("Wrong value of ICF name attribute in old  shadow", repoName,
                icfsNameAttribute.getRealValue());
    }
}
