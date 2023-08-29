package com.myorg;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iotsitewise.CfnAccessPolicy;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class UbuntuArmStack extends Stack {
    public UbuntuArmStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public UbuntuArmStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = Vpc.Builder.create(this, id + "-gs-vpc")
                .vpcName(id + "-vpc")
                .build();

        final ISecurityGroup securityGroup = SecurityGroup.Builder.create(this, id + "-gs-securitygroup")
                .securityGroupName(id)
                .vpc(vpc)
                .build();

        addSecurityGroupIngressRules(securityGroup);

        final Map<String, String> armUbuntuAMIs = new HashMap<>();
        armUbuntuAMIs.put("eu-west-2", "ami-0a47852af5dfa6b0f");

        final IMachineImage armUbuntuMachineImage = MachineImage.genericLinux(armUbuntuAMIs);

        IRole role = Role.fromRoleName(this, "GsBasicEC2", "BasicEC2");

        final Instance engineEC2Instance = Instance.Builder.create(this, id + "-ec2")
                .instanceName(id + "-ec2")
                .role(role)
                .machineImage(armUbuntuMachineImage)
                .securityGroup(securityGroup)
                .instanceType(InstanceType.of(
                        InstanceClass.BURSTABLE4_GRAVITON,
                        InstanceSize.SMALL
                ))
                .vpcSubnets(
                        SubnetSelection.builder()
                                .subnetType(SubnetType.PUBLIC)
                                .build()
                )
                .vpc(vpc)
                .build();

        String userData = null;
        try {
            userData = Files.readString(Path.of("src/main/resources/installNginx.sh"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        engineEC2Instance.addUserData(userData);
    }

    private void addSecurityGroupIngressRules(ISecurityGroup securityGroup) {
        List<Port> openPorts = List.of(Port.tcp(22), Port.tcp(80), Port.tcp(443));

        List<IPeer> cidrAddresses = List.of(Peer.ipv4("151.170.0.0/16"),
                        Peer.ipv4("185.251.11.180/32"),
                                Peer.ipv4("185.251.11.188/32"),
                                        Peer.ipv4("136.228.244.149/32"),
                                                Peer.ipv4("136.228.224.30/32"),
                                                        Peer.ipv4("185.251.11.167/32"),
                                                                Peer.ipv4("136.228.225.63/32"),
                                                                        Peer.ipv4("136.228.234.62/32"));

        cidrAddresses.forEach(peer -> openPorts.forEach(port -> securityGroup.addIngressRule(peer, port)));
    }
}
