package com.kwmobile.hometiming.data.firestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class HouseErrorTest {
    @Test
    public void mapsFirestoreCodes() {
        assertEquals(HouseError.NETWORK, HouseError.fromFirestoreCode("UNAVAILABLE"));
        assertEquals(HouseError.NETWORK, HouseError.fromFirestoreCode("DEADLINE_EXCEEDED"));
        assertEquals(HouseError.PERMISSION_DENIED, HouseError.fromFirestoreCode("PERMISSION_DENIED"));
        assertEquals(HouseError.NOT_FOUND, HouseError.fromFirestoreCode("NOT_FOUND"));
        assertEquals(HouseError.UNKNOWN, HouseError.fromFirestoreCode("INTERNAL"));
        assertEquals(HouseError.UNKNOWN, HouseError.fromFirestoreCode(null));
    }

    @Test
    public void rejectionsInsideTransactionsKeepTheirError() {
        Exception wrapped = new RuntimeException(new HouseException.Rejection(HouseError.HOUSE_NOT_EMPTY));

        assertEquals(HouseError.HOUSE_NOT_EMPTY, HouseRepository.classify(wrapped));
        assertEquals(HouseError.INVALID_CODE, HouseRepository.classify(new HouseException(HouseError.INVALID_CODE)));
        assertEquals(HouseError.UNKNOWN, HouseRepository.classify(null));
    }

    @Test
    public void everyErrorHasItsOwnMessage() {
        Set<Integer> messages = new HashSet<>();
        for (HouseError error : HouseError.values()) {
            assertNotEquals(error.name(), 0, error.messageRes);
            messages.add(error.messageRes);
        }
        assertEquals(HouseError.values().length, messages.size());
    }
}
