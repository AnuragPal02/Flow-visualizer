const TYPE_LABEL = {
  CONTROLLER: "Controller",
  SERVICE: "Service",
  REPOSITORY: "Repository",
  CLIENT: "External client",
  COMPONENT: "Component",
  OTHER: "Class",
};

const KIND_LABEL = {
  http: "HTTP",
  messaging: "Messaging",
  event: "Event",
  database: "Database",
  email: "Email",
};

export const typeLabel = (type) => TYPE_LABEL[type] || TYPE_LABEL.OTHER;
export const kindLabel = (kind) => KIND_LABEL[kind] || kind;
