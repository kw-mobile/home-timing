package com.kwmobile.hometiming.data.firestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ItemErrorTest {
    @Test
    public void mapsFirestoreCodes() {
        assertEquals(ItemError.NETWORK, ItemError.fromFirestoreCode("UNAVAILABLE"));
        assertEquals(ItemError.NETWORK, ItemError.fromFirestoreCode("DEADLINE_EXCEEDED"));
        assertEquals(ItemError.PERMISSION_DENIED, ItemError.fromFirestoreCode("PERMISSION_DENIED"));
        assertEquals(ItemError.PERMISSION_DENIED, ItemError.fromFirestoreCode("UNAUTHENTICATED"));
        assertEquals(ItemError.NOT_FOUND, ItemError.fromFirestoreCode("NOT_FOUND"));
        assertEquals(ItemError.ALREADY_COMPLETED, ItemError.fromFirestoreCode("ALREADY_EXISTS"));
        assertEquals(ItemError.UNKNOWN, ItemError.fromFirestoreCode("INTERNAL"));
        assertEquals(ItemError.UNKNOWN, ItemError.fromFirestoreCode(null));
    }

    @Test
    public void rejectionsInsideTransactionsKeepTheirError() {
        Exception wrapped = new RuntimeException(new ItemException.Rejection(ItemError.SCHEDULE_CHANGED));

        assertEquals(ItemError.SCHEDULE_CHANGED, ItemRepository.classify(wrapped));
        assertEquals(ItemError.ALREADY_COMPLETED,
                ItemRepository.classify(new ItemException(ItemError.ALREADY_COMPLETED)));
        assertEquals(ItemError.UNKNOWN, ItemRepository.classify(null));
    }

    @Test
    public void everyErrorHasItsOwnMessage() {
        Set<Integer> messages = new HashSet<>();
        for (ItemError error : ItemError.values()) {
            assertNotEquals(error.name(), 0, error.messageRes);
            messages.add(error.messageRes);
        }
        assertEquals(ItemError.values().length, messages.size());
    }
}
