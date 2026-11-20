package com.internship.testcontainers_postgres_manager.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class SandboxRegistry {
	
private final Map<String,SandboxRecord> sandboxes=new ConcurrentHashMap<>();

private final AtomicInteger idCounter=new AtomicInteger();

public String nextSandboxId() 
{
	return String.format("sbx-%03d",idCounter.incrementAndGet());
}

public void add(SandboxRecord record) {
	sandboxes.put(record.getSandboxId(),record);
}

public Optional<SandboxRecord>find(String sandboxId) 
{
return Optional.ofNullable(sandboxes.get(sandboxId));
}

public Optional<SandboxRecord>remove(String sandboxId)
{
return Optional.ofNullable(sandboxes.remove(sandboxId));
}

public int count()
{
	return sandboxes.size();
}
}
