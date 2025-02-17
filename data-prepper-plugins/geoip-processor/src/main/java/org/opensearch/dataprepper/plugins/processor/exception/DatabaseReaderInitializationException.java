/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.exception;

public class DatabaseReaderInitializationException extends EngineFailureException {
    public DatabaseReaderInitializationException(final String exceptionMsg) {
        super(exceptionMsg);
    }
}
