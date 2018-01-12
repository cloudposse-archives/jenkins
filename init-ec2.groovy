#!groovy

import com.amazonaws.services.ec2.model.*
import hudson.model.*
import hudson.plugins.ec2.*
import jenkins.model.*

println "init-ec2.groovy: Starting..."

def instance = Jenkins.getInstance()
def env = System.getenv()

def cloud_name = env.JENKINS_SLAVE_CLOUD_NAME
def region = env.JENKINS_SLAVE_REGION
def instance_cap = env.JENKINS_SLAVE_INSTANCE_CAP
def ami_id = env.JENKINS_SLAVE_AMI_ID
def security_groups = env.JENKINS_SLAVE_SECURITY_GROUPS
def subnet_id = env.JENKINS_SLAVE_SUBNET_ID
def instance_profile_arn = env.JENKINS_SLAVE_INSTANCE_PROFILE_ARN ?: ''
def instance_type = env.JENKINS_SLAVE_INSTANCE_TYPE ?: 't2.small'
def number_of_executors = env.JENKINS_SLAVE_NUM_EXECUTORS ?: '4'
def init_script = env.JENKINS_SLAVE_INIT_SCRIPT ?: ''
def user_data = env.JENKINS_SLAVE_USER_DATA ?: ''
def remote_admin = env.JENKINS_SLAVE_REMOTE_ADMIN ?: 'root'
def idle_termination_minutes = env.JENKINS_SLAVE_IDLE_TERMINATION_MINUTES ?: '30'
def launch_timeout_seconds = env.JENKINS_SLAVE_LAUNCH_TIMEOUT_SECONDS ?: '300'
def private_key = env.JENKINS_SLAVE_PRIVATE_KEY ? new String(env.JENKINS_SLAVE_PRIVATE_KEY.decodeBase64()) : ''
def stop_on_idle_timeout = env.JENKINS_SLAVE_STOP_ON_IDLE_TIMEOUT ? env.JENKINS_SLAVE_STOP_ON_IDLE_TIMEOUT.toBoolean() : true
def ec2_tags = [new EC2Tag('Name', cloud_name)]

SpotConfiguration spot_config = null
AMITypeData ami_type = null


def slave_template = new SlaveTemplate(
        // String ami
        ami_id,
        // String zone
        '',
        // SpotConfiguration spotConfig
        spot_config,
        // String securityGroups
        security_groups,
        // String remoteFS
        '',
        // InstanceType type
        InstanceType.fromValue(instance_type),
        // boolean ebsOptimized
        false,
        // String labelString
        cloud_name,
        // Node.Mode mode
        Node.Mode.NORMAL,
        // String description
        cloud_name,
        // String initScript
        init_script,
        // String tmpDir
        '',
        // String userData
        user_data,
        // String numExecutors
        number_of_executors,
        // String remoteAdmin
        remote_admin,
        // AMITypeData
        ami_type,
        // String jvmopts
        '',
        // boolean stopOnIdleTimeout
        stop_on_idle_timeout,
        // String subnetId
        subnet_id,
        // List<EC2Tag> tags
        ec2_tags,
        // String idleTerminationMinutes
        idle_termination_minutes,
        // boolean usePrivateDnsName
        true,
        // String instanceCapStr
        instance_cap,
        // String iamInstanceProfile
        instance_profile_arn,
        // boolean useEphemeralDevices
        true,
        // boolean useDedicatedTenancy
        false,
        // String launchTimeoutStr
        launch_timeout_seconds,
        // boolean associatePublicIp
        false,
        // String customDeviceMapping
        '',
        // boolean connectBySSHProcess
        true
)

def ec2_cloud = new AmazonEC2Cloud(
        // String cloudName
        cloud_name,
        // boolean useInstanceProfileForCredentials
        true,
        // String credentialsId
        '',
        // String region
        region,
        // String privateKey
        private_key,
        // String instanceCapStr
        instance_cap,
        // List<SlaveTemplate> templates
        [slave_template]
)

instance.clouds.clear()
instance.clouds.add(ec2_cloud)
println "init-ec2.groovy: Configured EC2 cloud"
