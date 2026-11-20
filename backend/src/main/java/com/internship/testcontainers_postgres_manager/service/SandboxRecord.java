package com.internship.testcontainers_postgres_manager.service;

import java.time.Instant;

import org.testcontainers.postgresql.PostgreSQLContainer;

import com.internship.testcontainers_postgres_manager.dto.SandboxStatus;

public class SandboxRecord {

private final String sandboxId;
private final String name;
private final Instant createdAt;
private final PostgreSQLContainer container;

//volatile: a change made by one request thread is immediately visible to the others
private volatile SandboxStatus status;

public SandboxRecord(String sandboxId,String name,Instant createdAt,PostgreSQLContainer container,SandboxStatus status) {
	this.sandboxId=sandboxId;
	this.name=name;
	this.createdAt=createdAt;
	this.container=container;
	this.status=status;
}

public String getSandboxId() 
{
	return sandboxId;
}

public String getName()
{
	return name;
}

public Instant getCreatedAt() 
{
	return createdAt;
}

public PostgreSQLContainer getContainer()
{
	return container;
}

public SandboxStatus getStatus()
{
	return status;
}
public void setStatus(SandboxStatus status) 
{ 
	this.status = status; 
}
}
