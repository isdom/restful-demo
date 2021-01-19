package org.jocean.restfuldemo.ctrl;

import static org.junit.Assert.assertEquals;

import org.jocean.aliyun.oss.OssAPI;
import org.jocean.aliyun.oss.OssAPI.GetObjectBuilder;
import org.jocean.aliyun.oss.OssBucket;
import org.jocean.aliyun.sts.STSCredentials;
import org.jocean.http.Interact;
import org.junit.Test;

import mockit.Expectations;
import mockit.Mocked;
import rx.Observable.Transformer;

public class OssDemoTestCase {

    @Test(expected = NullPointerException.class)
    public final void testGetobjUninited() {
        final OssDemo ossDemo = new OssDemo();

        ossDemo.getobj("111");
    }

    @Mocked
    OssAPI ossapi;

    @Mocked
    GetObjectBuilder getbuilder;

    @Mocked
    STSCredentials stsc;

    @Test//(expected = NullPointerException.class)
    public final void testGetobjNormal() {
        new Expectations() {
            {
                getbuilder.signer((Transformer<Interact, Interact>) any);
                result = getbuilder;

                getbuilder.bucket((OssBucket) any);
                result = getbuilder;

                getbuilder.object(anyString);
                result = getbuilder;

                ossapi.getObject();
                result = getbuilder;
            }
        };

        assertEquals(ossapi.getObject(), getbuilder);

        final OssDemo ossCtrl = new OssDemo();

        ossCtrl.oss = ossapi;
        ossCtrl._stsc = stsc;

        ossCtrl.getobj("111");
    }
}
