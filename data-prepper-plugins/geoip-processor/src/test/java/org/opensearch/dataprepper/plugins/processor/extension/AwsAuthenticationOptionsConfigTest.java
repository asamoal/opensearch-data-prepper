/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.opensearch.dataprepper.test.helper.ReflectivelySetField;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AwsAuthenticationOptionsConfigTest {
    private ObjectMapper objectMapper;

    private AwsAuthenticationOptionsConfig awsAuthenticationOptionsConfig;
    private final String TEST_ROLE = "arn:aws:iam::123456789012:role/test-role";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        awsAuthenticationOptionsConfig = new AwsAuthenticationOptionsConfig();
    }

    @ParameterizedTest
    @ValueSource(strings = {"us-east-1", "us-west-2", "eu-central-1"})
    void getAwsRegion_returns_Region_of(final String regionString) {
        final Region expectedRegionObject = Region.of(regionString);
        final Map<String, Object> jsonMap = Map.of("region", regionString);
        final AwsAuthenticationOptionsConfig objectUnderTest = objectMapper.convertValue(jsonMap, AwsAuthenticationOptionsConfig.class);
        assertThat(objectUnderTest.getAwsRegion(), equalTo(expectedRegionObject));
    }

    @Test
    void getAwsRegion_returns_null_when_region_is_null() {
        final Map<String, Object> jsonMap = Collections.emptyMap();
        final AwsAuthenticationOptionsConfig objectUnderTest = objectMapper.convertValue(jsonMap, AwsAuthenticationOptionsConfig.class);
        assertThat(objectUnderTest.getAwsRegion(), nullValue());
    }

    @Test
    void authenticateAWSConfiguration_should_return_s3Client_without_sts_role_arn() throws NoSuchFieldException, IllegalAccessException {
        ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsRegion", "us-east-1");
        ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsStsRoleArn", null);

        final DefaultCredentialsProvider mockedCredentialsProvider = mock(DefaultCredentialsProvider.class);
        final AwsCredentialsProvider actualCredentialsProvider;
        try (final MockedStatic<DefaultCredentialsProvider> defaultCredentialsProviderMockedStatic = mockStatic(DefaultCredentialsProvider.class)) {
            defaultCredentialsProviderMockedStatic.when(DefaultCredentialsProvider::create)
                    .thenReturn(mockedCredentialsProvider);
            actualCredentialsProvider = awsAuthenticationOptionsConfig.authenticateAwsConfiguration();
        }

        assertThat(actualCredentialsProvider, sameInstance(mockedCredentialsProvider));
    }

    @Nested
    class WithSts {
        private StsClient stsClient;
        private StsClientBuilder stsClientBuilder;

        @BeforeEach
        void setUp() {
            stsClient = mock(StsClient.class);
            stsClientBuilder = mock(StsClientBuilder.class);

            when(stsClientBuilder.build()).thenReturn(stsClient);
        }

        @Test
        void authenticateAWSConfiguration_should_return_s3Client_with_sts_role_arn() throws NoSuchFieldException, IllegalAccessException {
            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsRegion", "us-east-1");
            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsStsRoleArn", TEST_ROLE);

            when(stsClientBuilder.region(Region.US_EAST_1)).thenReturn(stsClientBuilder);
            final AssumeRoleRequest.Builder assumeRoleRequestBuilder = mock(AssumeRoleRequest.Builder.class);
            when(assumeRoleRequestBuilder.roleSessionName(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);
            when(assumeRoleRequestBuilder.roleArn(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);

            final AwsCredentialsProvider actualCredentialsProvider;
            try (final MockedStatic<StsClient> stsClientMockedStatic = mockStatic(StsClient.class);
                 final MockedStatic<AssumeRoleRequest> assumeRoleRequestMockedStatic = mockStatic(AssumeRoleRequest.class)) {
                stsClientMockedStatic.when(StsClient::builder).thenReturn(stsClientBuilder);
                assumeRoleRequestMockedStatic.when(AssumeRoleRequest::builder).thenReturn(assumeRoleRequestBuilder);
                actualCredentialsProvider = awsAuthenticationOptionsConfig.authenticateAwsConfiguration();
            }

            assertThat(actualCredentialsProvider, instanceOf(AwsCredentialsProvider.class));

            verify(assumeRoleRequestBuilder).roleArn(TEST_ROLE);
            verify(assumeRoleRequestBuilder).roleSessionName(anyString());
            verify(assumeRoleRequestBuilder).build();
            verifyNoMoreInteractions(assumeRoleRequestBuilder);
        }

        @Test
        void authenticateAWSConfiguration_should_return_s3Client_with_sts_role_arn_when_no_region() throws NoSuchFieldException, IllegalAccessException {
            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsRegion", null);
            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsStsRoleArn", TEST_ROLE);
            assertThat(awsAuthenticationOptionsConfig.getAwsRegion(), equalTo(null));

            when(stsClientBuilder.region(null)).thenReturn(stsClientBuilder);

            final AwsCredentialsProvider actualCredentialsProvider;
            try (final MockedStatic<StsClient> stsClientMockedStatic = mockStatic(StsClient.class)) {
                stsClientMockedStatic.when(StsClient::builder).thenReturn(stsClientBuilder);
                actualCredentialsProvider = awsAuthenticationOptionsConfig.authenticateAwsConfiguration();
            }

            assertThat(actualCredentialsProvider, instanceOf(AwsCredentialsProvider.class));
        }

        @Test
        void authenticateAWSConfiguration_should_override_STS_Headers_when_HeaderOverrides_when_set() throws NoSuchFieldException, IllegalAccessException {
            final String headerName1 = UUID.randomUUID().toString();
            final String headerValue1 = UUID.randomUUID().toString();
            final String headerName2 = UUID.randomUUID().toString();
            final String headerValue2 = UUID.randomUUID().toString();
            final Map<String, String> overrideHeaders = Map.of(headerName1, headerValue1, headerName2, headerValue2);

            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsRegion", "us-east-1");
            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsStsRoleArn", TEST_ROLE);
            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsStsHeaderOverrides", overrideHeaders);

            when(stsClientBuilder.region(Region.US_EAST_1)).thenReturn(stsClientBuilder);

            final AssumeRoleRequest.Builder assumeRoleRequestBuilder = mock(AssumeRoleRequest.Builder.class);
            when(assumeRoleRequestBuilder.roleSessionName(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);
            when(assumeRoleRequestBuilder.roleArn(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);
            when(assumeRoleRequestBuilder.overrideConfiguration(any(Consumer.class)))
                    .thenReturn(assumeRoleRequestBuilder);

            final AwsCredentialsProvider actualCredentialsProvider;
            try (final MockedStatic<StsClient> stsClientMockedStatic = mockStatic(StsClient.class);
                 final MockedStatic<AssumeRoleRequest> assumeRoleRequestMockedStatic = mockStatic(AssumeRoleRequest.class)) {
                stsClientMockedStatic.when(StsClient::builder).thenReturn(stsClientBuilder);
                assumeRoleRequestMockedStatic.when(AssumeRoleRequest::builder).thenReturn(assumeRoleRequestBuilder);
                actualCredentialsProvider = awsAuthenticationOptionsConfig.authenticateAwsConfiguration();
            }

            assertThat(actualCredentialsProvider, instanceOf(AwsCredentialsProvider.class));

            final ArgumentCaptor<Consumer<AwsRequestOverrideConfiguration.Builder>> configurationCaptor = ArgumentCaptor.forClass(Consumer.class);

            verify(assumeRoleRequestBuilder).roleArn(TEST_ROLE);
            verify(assumeRoleRequestBuilder).roleSessionName(anyString());
            verify(assumeRoleRequestBuilder).overrideConfiguration(configurationCaptor.capture());
            verify(assumeRoleRequestBuilder).build();
            verifyNoMoreInteractions(assumeRoleRequestBuilder);

            final Consumer<AwsRequestOverrideConfiguration.Builder> actualOverride = configurationCaptor.getValue();

            final AwsRequestOverrideConfiguration.Builder configurationBuilder = mock(AwsRequestOverrideConfiguration.Builder.class);
            actualOverride.accept(configurationBuilder);
            verify(configurationBuilder).putHeader(headerName1, headerValue1);
            verify(configurationBuilder).putHeader(headerName2, headerValue2);
            verifyNoMoreInteractions(configurationBuilder);
        }

        @Test
        void authenticateAWSConfiguration_should_not_override_STS_Headers_when_HeaderOverrides_are_empty() throws NoSuchFieldException, IllegalAccessException {

            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsRegion", "us-east-1");
            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsStsRoleArn", TEST_ROLE);
            ReflectivelySetField.setField(AwsAuthenticationOptionsConfig.class, awsAuthenticationOptionsConfig, "awsStsHeaderOverrides", Collections.emptyMap());

            when(stsClientBuilder.region(Region.US_EAST_1)).thenReturn(stsClientBuilder);
            final AssumeRoleRequest.Builder assumeRoleRequestBuilder = mock(AssumeRoleRequest.Builder.class);
            when(assumeRoleRequestBuilder.roleSessionName(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);
            when(assumeRoleRequestBuilder.roleArn(anyString()))
                    .thenReturn(assumeRoleRequestBuilder);

            final AwsCredentialsProvider actualCredentialsProvider;
            try (final MockedStatic<StsClient> stsClientMockedStatic = mockStatic(StsClient.class);
                 final MockedStatic<AssumeRoleRequest> assumeRoleRequestMockedStatic = mockStatic(AssumeRoleRequest.class)) {
                stsClientMockedStatic.when(StsClient::builder).thenReturn(stsClientBuilder);
                assumeRoleRequestMockedStatic.when(AssumeRoleRequest::builder).thenReturn(assumeRoleRequestBuilder);
                actualCredentialsProvider = awsAuthenticationOptionsConfig.authenticateAwsConfiguration();
            }

            assertThat(actualCredentialsProvider, instanceOf(AwsCredentialsProvider.class));

            verify(assumeRoleRequestBuilder).roleArn(TEST_ROLE);
            verify(assumeRoleRequestBuilder).roleSessionName(anyString());
            verify(assumeRoleRequestBuilder).build();
            verifyNoMoreInteractions(assumeRoleRequestBuilder);
        }
    }

}