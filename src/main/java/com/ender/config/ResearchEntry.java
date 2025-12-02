package com.ender.config;

import java.util.List;

// Simple POJO representing a research entry loaded from JSON
public class ResearchEntry {
    public String id;
    public String title;
    public String description;
    public int time_seconds;
    public List<Requirement> requirements;
    public Reward reward;
    public List<Reward> rewards; // optional multi-reward support

    public static class Requirement {
        public String type; // "item" or "research"
        public String item;
        public int count;
        public String research_id;
    }

    public static class Reward {
        public String item;
        public int count;
        public String nbt; // optional SNBT payload applied to the stack
    }
}