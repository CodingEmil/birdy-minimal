package dev.birdy.minimal;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;

// Contacts for the drawer search (optional, needs READ_CONTACTS). Read on the device only, never stored.
final class ContactSearch {
    static final int MAX = 3;

    static final class Contact {
        final String name;
        final Uri uri; // opens the contact card

        Contact(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    interface Source {
        List<Contact> find(Context context, String query);
    }

    // The provider lookup; replaced in tests.
    static final Source PROVIDER = ContactSearch::query;
    static Source source = PROVIDER;

    private ContactSearch() {}

    static boolean allowed(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    // Up to MAX contacts for the query, names starting with it first (as for apps); empty when off or not allowed.
    static List<Contact> find(Context context, String query, boolean enabled) {
        List<Contact> out = new ArrayList<>();
        if (!enabled || query.trim().isEmpty() || !allowed(context)) return out;
        List<Contact> later = new ArrayList<>();
        for (Contact c : source.find(context, query.trim())) {
            int rank = Search.rank(c.name, query);
            if (rank == Search.START) out.add(c);
            else if (rank == Search.WORD) later.add(c);
        }
        out.addAll(later);
        return out.size() > MAX ? new ArrayList<>(out.subList(0, MAX)) : out;
    }

    private static List<Contact> query(Context context, String query) {
        List<Contact> out = new ArrayList<>();
        Uri filter = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query));
        String[] columns = {ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY};
        ContentResolver resolver = context.getContentResolver();
        try (Cursor c = resolver.query(filter, columns, null, null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) {
            while (c != null && c.moveToNext() && out.size() < 20) {
                String name = c.getString(2);
                if (name == null) continue;
                out.add(new Contact(name, ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))));
            }
        } catch (RuntimeException e) { // provider missing or permission revoked meanwhile
            out.clear();
        }
        return out;
    }
}
