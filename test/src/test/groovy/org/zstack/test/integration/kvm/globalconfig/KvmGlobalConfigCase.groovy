package org.zstack.test.integration.kvm.globalconfig


import org.zstack.sdk.CreateVmInstanceAction
import org.zstack.sdk.GetCpuMemoryCapacityAction
import org.zstack.sdk.GlobalConfigInventory
import org.zstack.sdk.UpdateGlobalConfigAction
import org.zstack.test.integration.kvm.Env
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.HostSpec
import org.zstack.testlib.ImageSpec
import org.zstack.testlib.InstanceOfferingSpec
import org.zstack.testlib.L3NetworkSpec
import org.zstack.testlib.SubCase
import org.zstack.testlib.Test
import org.zstack.utils.SizeUtils
import org.zstack.utils.data.SizeUnit
import static java.util.Arrays.asList;

class KvmGlobalConfigCase extends SubCase {
    EnvSpec env

    @Override
    void setup() {
        spring {
            sftpBackupStorage()
            localStorage()
            virtualRouter()
            securityGroup()
            kvm()
        }
    }

    @Override
    void environment() {
        env = Env.noVmEnv()
    }

    @Override
    void test() {
        env.create {
            testLargeHostReservedMemory()
            testReservedHostCapacityAndThenCreateVmFailure()
            testReservedHostCapacityAndThenCreateVmSuccess()
            testReservedHostCapacityAndThenCreateVmSuccessWhenOverProvisioningMemory()
        }
    }

    void testLargeHostReservedMemory() {
        def action = new UpdateGlobalConfigAction()
        action.category = "kvm"
        action.name = "reservedMemory"
        action.value = "2T"
        action.sessionId = Test.currentEnvSpec.session.uuid
        UpdateGlobalConfigAction.Result res = action.call()
        assert res.error != null

        def action2 = new UpdateGlobalConfigAction()
        action2.category = "kvm"
        action2.name = "reservedMemory"
        action2.value = "1025G"
        action2.sessionId = Test.currentEnvSpec.session.uuid
        UpdateGlobalConfigAction.Result res2 = action2.call()
        assert res2.error != null

        def action3 = new UpdateGlobalConfigAction()
        action3.category = "kvm"
        action3.name = "reservedMemory"
        action3.value = "10000000000G"
        action3.sessionId = Test.currentEnvSpec.session.uuid

        UpdateGlobalConfigAction.Result res3 = action3.call()
        assert res3.error != null

        def action4 = new UpdateGlobalConfigAction()
        action4.category = "kvm"
        action4.name = "reservedMemory"
        action4.value = "-1G"
        action4.sessionId = Test.currentEnvSpec.session.uuid

        UpdateGlobalConfigAction.Result res4 = action4.call()
        assert res4.error != null
    }

    void testReservedHostCapacityAndThenCreateVmFailure(){
        HostSpec hostSpec = env.specByName("kvm")
        hostSpec.totalCpu = 40
        hostSpec.totalMem = SizeUnit.GIGABYTE.toByte(8)
        GlobalConfigInventory inv = updateGlobalConfig {
            category = "kvm"
            name = "reservedMemory"
            value = "2G"
        }
        def action = new CreateVmInstanceAction()
        action.name = "vm1"
        action.instanceOfferingUuid = (env.specByName("instanceOffering")as InstanceOfferingSpec).inventory.uuid
        action.imageUuid = (env.specByName("image1") as ImageSpec).inventory.uuid
        action.l3NetworkUuids = [(env.specByName("l3") as L3NetworkSpec).inventory.uuid]
        action.hostUuid = hostSpec.inventory.uuid
        action.sessionId =  adminSession()
        CreateVmInstanceAction.Result res = action.call()
        assert res.error !=null
    }
    void testReservedHostCapacityAndThenCreateVmSuccess(){
        HostSpec hostSpec = env.specByName("kvm")
        hostSpec.totalCpu = 40
        hostSpec.totalMem = SizeUnit.GIGABYTE.toByte(8)
        GlobalConfigInventory inv = updateGlobalConfig {
            category = "kvm"
            name = "reservedMemory"
            value = "2G"
        }
        def action = new CreateVmInstanceAction()
        action.name = "vm1"
        action.instanceOfferingUuid = (env.specByName("1CPU-2G")as InstanceOfferingSpec).inventory.uuid
        action.imageUuid = (env.specByName("image1") as ImageSpec).inventory.uuid
        action.l3NetworkUuids = [(env.specByName("l3") as L3NetworkSpec).inventory.uuid]
        action.hostUuid = hostSpec.inventory.uuid
        action.sessionId =  adminSession()
        CreateVmInstanceAction.Result res = action.call()
        assert res.error ==null
        action.name = "vm2"
        res = action.call()
        assert res.error ==null
        action.name = "vm3"
        res = action.call()
        assert res.error ==null
        GetCpuMemoryCapacityAction action2 = new GetCpuMemoryCapacityAction()
        action2.hostUuids = asList(hostSpec.inventory.uuid)
        action2.sessionId = adminSession()
        GetCpuMemoryCapacityAction.Result res2 = action2.call()
        res2.error == null
        assert res2.value.availableMemory ==  SizeUtils.sizeStringToBytes("2G")
    }
    void testReservedHostCapacityAndThenCreateVmSuccessWhenOverProvisioningMemory(){
        HostSpec hostSpec = env.specByName("kvm")
        hostSpec.totalCpu = 40
        hostSpec.totalMem = SizeUnit.GIGABYTE.toByte(8)
        GlobalConfigInventory inv = updateGlobalConfig {
            category = "kvm"
            name = "reservedMemory"
            value = "2G"
        }
        GlobalConfigInventory inv2 = updateGlobalConfig {
            category = "mevoco"
            name = "overProvisioning.memory"
            value = "2"
        }
        def action = new CreateVmInstanceAction()
        action.name = "vm1"
        action.instanceOfferingUuid = (env.specByName("1CPU-4G")as InstanceOfferingSpec).inventory.uuid
        action.imageUuid = (env.specByName("image1") as ImageSpec).inventory.uuid
        action.l3NetworkUuids = [(env.specByName("l3") as L3NetworkSpec).inventory.uuid]
        action.hostUuid = hostSpec.inventory.uuid
        action.sessionId =  adminSession()
        CreateVmInstanceAction.Result res = action.call()
        assert res.error ==null
        action.name = "vm2"
        res = action.call()
        assert res.error ==null
        action.name = "vm3"
        res = action.call()
        assert res.error ==null
        GetCpuMemoryCapacityAction action2 = new GetCpuMemoryCapacityAction()
        action2.hostUuids = asList(hostSpec.inventory.uuid)
        action2.sessionId = adminSession()
        GetCpuMemoryCapacityAction.Result res2 = action2.call()
        res2.error == null
        assert res2.value.availableMemory ==  SizeUtils.sizeStringToBytes("2G")
    }
    @Override
    void clean() {
        env.delete()
    }
}
