protoc --java_out=/Users/lixinzhuang1/IdeaProjects/paxos_demo/src/main/java/org/lxz/proto/paxoskv paxoskv.proto

protoc --plugin=protoc-gen-grpc-java=path/to/protoc-gen-grpc-java --grpc-java_out=path/to/output/directory path/to/your_service.proto
