package com.mama.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;

/** Intent builders for the system contacts picker and the dialer. Neither needs a permission. */
public final class Intents {
    private Intents() {
    }

    /** Opens the system picker over contacts that have a phone number. The result carries a one-contact read grant. */
    public static Intent pickPhoneContact() {
        return new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
    }

    /** Opens the dialer with the number filled in. The user taps call there. */
    public static Intent dial(String phone) {
        return new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone.trim(), null));
    }
}
