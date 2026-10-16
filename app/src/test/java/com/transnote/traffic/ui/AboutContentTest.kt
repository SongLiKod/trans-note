package com.transnote.traffic.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 逐字锁定「关于」页的使用声明，防止展示文本被误改。
 * 若确需修改声明，请同步更新 [AboutContent.DISCLAIMER] 与本测试中的期望文本。
 */
class AboutContentTest {

    @Test
    fun disclaimer_isLockedVerbatim() {
        val expected = """
            本软件（途记交通备忘录 TransNote，包名 com.transnote.traffic）为作者个人兴趣开发的非商业软件，仅供个人学习、娱乐及非商业性质的免费使用。

            一、知识产权
            作者保留本软件及其源代码、界面、文档等全部知识产权。未经作者书面许可，任何人不得对本软件进行二次开发、修改、复刻、衍生创作，或将其用于任何商业营利用途。

            二、使用规范
            使用者应遵守其所在地的法律法规以及网络使用规范。因使用或滥用本软件所产生的一切后果，均由使用者自行承担，作者不承担任何责任。

            三、数据与隐私
            本软件所有数据均保存在使用者设备本地，不上传服务器。作者不对因设备故障、误操作、系统清理或卸载导致的数据丢失负责，请使用者自行定期备份。

            四、免责声明
            本软件按“现状”提供，作者不对其适用性、准确性或可用性作任何明示或默示担保。使用本软件即表示使用者已阅读并同意本声明。
        """.trimIndent()

        assertEquals("使用声明文本被改动，请同步更新 AboutContent 与本测试", expected, AboutContent.DISCLAIMER)
        assertTrue(AboutContent.DISCLAIMER.contains("知识产权"))
        assertTrue(AboutContent.DISCLAIMER.contains("自行承担"))
    }
}
