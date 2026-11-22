package com.example.scamguard;
import android.content.Context;
import org.pytorch.executorch.EValue;
import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ModelRunner {
    private static ModelRunner instance;
    private Module module;

    public static synchronized ModelRunner get(Context c) throws Exception {
        if (instance == null) {
            ModelRunner r = new ModelRunner();
            r.load(c.getApplicationContext());
            instance = r;
        }
        return instance;
    }

    private void load(Context c) throws Exception {
        File f = new File(c.getFilesDir(), "sms_charcnn.pte");
        if (!f.exists()) {   // Module needs a real file path
            try (InputStream in = c.getAssets().open("sms_charcnn.pte");
                 OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
        module = Module.load(f.getAbsolutePath());
    }

    /** Returns raw model outputs [ham, spam]. */
    public synchronized float[] run(long[] ids) {
        Tensor input = Tensor.fromBlob(ids, new long[]{1, ids.length});
        EValue[] out = module.forward(EValue.from(input));
        return out[0].toTensor().getDataAsFloatArray();
    }
}