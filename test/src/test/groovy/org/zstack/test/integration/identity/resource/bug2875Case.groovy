package org.zstack.test.integration.identity.resource

import org.zstack.sdk.AccountInventory
import org.zstack.sdk.CreateAccountAction
import org.zstack.sdk.EipInventory
import org.zstack.test.integration.identity.IdentityTest
import org.zstack.test.integration.networkservice.provider.NetworkServiceProviderTest
import org.zstack.test.integration.networkservice.provider.virtualrouter.VirtualRouterNetworkServiceEnv
import org.zstack.testlib.EipSpec
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SpringSpec
import org.zstack.testlib.SubCase

/**
 * Created by camile on 2017/5/3.
 */
class bug2875Case extends SubCase {
    EnvSpec env

    static SpringSpec springSpec = makeSpring {
        localStorage()
        sftpBackupStorage()
        portForwarding()
        virtualRouter()
        flatNetwork()
        eip()
        lb()
        vyos()
        kvm()
    }

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(springSpec)
        useSpring(IdentityTest.springSpec)
        useSpring(NetworkServiceProviderTest.springSpec)
    }

    @Override
    void environment() {
        env = VirtualRouterNetworkServiceEnv.oneVmOneHostVyosOnEipEnv()
}

    @Override
    void test() {
        env.create {
            testChangeOwner()
        }
    }

    void testChangeOwner() {
        EipSpec eipSpec = env.specByName("eip")
        CreateAccountAction createAccountAction = new CreateAccountAction()
        createAccountAction.name = "test"
        createAccountAction.password = "password"
        createAccountAction.sessionId = adminSession()
        CreateAccountAction.Result res = createAccountAction.call()
        assert res.error == null
        AccountInventory testAccout1 = res.value.inventory
        createAccountAction.name = "test2"
        res = createAccountAction.call()
        assert res.error == null
        AccountInventory testAccout2 = res.value.inventory

        changeResourceOwner {
            accountUuid = testAccout1.uuid
            resourceUuid = eipSpec.inventory.uuid
        }
        deleteAccount {
            uuid  = testAccout1.uuid
        }
        changeResourceOwner {
            accountUuid = testAccout2.uuid
            resourceUuid = eipSpec.inventory.uuid
        }
    }
}
