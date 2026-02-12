package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

public class PrintAdapterHelper {
    public static void drawPdf(PrintDocumentAdapter adapter, PrintAttributes attributes, final ParcelFileDescriptor pfd, final Runnable onSuccess, final Runnable onFailure) {
        adapter.onLayout(null, attributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, pfd, null, new PrintDocumentAdapter.WriteResultCallback() {
                    @Override
                    public void onWriteFinished(PageRange[] pages) {
                        onSuccess.run();
                    }

                    @Override
                    public void onWriteFailed(CharSequence error) {
                        onFailure.run();
                    }
                });
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                onFailure.run();
            }
        }, null);
    }
}
