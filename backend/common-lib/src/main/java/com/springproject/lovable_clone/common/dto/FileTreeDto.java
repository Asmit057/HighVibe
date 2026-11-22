package com.springproject.lovable_clone.common.dto;

import java.io.Serializable;
import java.util.List;

public record FileTreeDto(List<FileNode> files) implements Serializable {
}
