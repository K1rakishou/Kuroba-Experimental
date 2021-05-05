package com.github.k1rakishou.chan.core.lib;

import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostParserContext;
import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostThreadParsed;
import com.github.k1rakishou.chan.core.lib.data.post_parsing.ThreadToParse;

/**
 * An interface for the native library where performance critical code is located (like post parsing
 * or filter matching (which will be added in the future)).
 *
 * When updating/renaming or moving this class to other place, don't forget to update the
 * kuroba_ex_native library.
 *
 * To build the library run build_libs.py script that is located in the kuroba_ex_native. On windows
 * you must use WSL to build it, meaning the script must be executed from within the WSL. It should
 * automatically download all the needed stuff, unpack it, build the libraries and copy them into
 * the jniLibs directory.
 *
 * javap -s PostThreadParsed to see class JNI signature
 * */
public class KurobaNativeLib {
    static {
        System.loadLibrary("kuroba_ex_native");
        init();
    }

    /**
     * Initializes the native library logger
     * */
    public static native void init();

    public static native PostThreadParsed parseThreadPosts(
            PostParserContext postParserContext,
            ThreadToParse threadToParse
    );
}
